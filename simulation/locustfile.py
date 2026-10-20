"""A single-process workload composed of ordinary weighted Locust users."""

import logging
import random
from urllib.parse import urlencode

import gevent
from locust import HttpUser, between, events, task
from locust.exception import StopUser
from locust.runners import LocalRunner

from common import (Api, BASE_URL, EDITABLE_INFORMATION, ITEM_LIMIT, ROOT,
                    RequestFailure, SimulationError, State, create_business, create_item,
                    load_catalogs, load_data, reconcile, register, target, validate)

log = logging.getLogger(__name__)
state = None
people = clusters = catalogs = None


def fail_run(environment, message):
    log.error("Simulation stopped: %s", message)
    environment.process_exit_code = 1
    gevent.spawn(environment.runner.quit)


@events.init.add_listener
def initialize(environment, **kwargs):
    global state
    try:
        target(environment.host)
        if not isinstance(environment.runner, LocalRunner):
            raise SimulationError("Only a single local Locust process is supported")
        state = State()
        state.lock()
        (ROOT / "results").mkdir(exist_ok=True)
        original_start = environment.runner.start

        def checked_start(user_count, *args, **kwargs):
            try:
                target(environment.host)
                if not 0 <= user_count <= 100:
                    raise SimulationError("Simulation supports at most 100 concurrent virtual users")
                if not state.data["initialized"]:
                    raise SimulationError("Complete bootstrap before starting continuous activity")
            except SimulationError as error:
                fail_run(environment, str(error))
                return
            return original_start(user_count, *args, **kwargs)

        environment.runner.start = checked_start
        # The UI otherwise returns HTTP 500 for an invalid count/host.
        if environment.web_ui:
            from flask import jsonify, request
            original_swarm = environment.web_ui.app.view_functions["locust.swarm"]

            def checked_swarm():
                try:
                    count = int(request.form.get("user_count", "0"))
                    if not 0 <= count <= 100:
                        raise ValueError("User count must be between 0 and 100")
                    target(request.form.get("host", environment.host))
                except (ValueError, SimulationError) as error:
                    return jsonify(success=False, message=str(error)), 400
                return original_swarm()

            environment.web_ui.app.view_functions["locust.swarm"] = checked_swarm
    except SimulationError as error:
        environment.runner.start = lambda *args, **kwargs: None
        fail_run(environment, str(error))


@events.test_start.add_listener
def prepare(environment, **kwargs):
    global people, clusters, catalogs
    try:
        target(environment.host)
        if state is None or not state.data["initialized"]:
            raise SimulationError("Complete bootstrap before starting continuous activity")
        api = Api(transport_for(environment))
        catalogs = load_catalogs(api)
        people, clusters = load_data(catalogs)
        # Reconcile persisted ownership before making capacity decisions.
        for account in state.data["accounts"]:
            api.login(account)
            business, items = reconcile(api, state, account)
            if account["protected"] and (not business or sum(i["status"] == "ACTIVE" for i in items) < 2):
                raise SimulationError("Protected bootstrap data is missing; inspect or explicitly reset")
    except SimulationError as error:
        fail_run(environment, str(error))


@events.quitting.add_listener
def close(**kwargs):
    if state:
        state.close()


def transport_for(environment, client=None):
    target(environment.host)
    if client is None:
        from locust.clients import HttpSession
        client = HttpSession(BASE_URL, environment.events.request, None)
    client.trust_env = False

    def send(method, path, payload, token, expected, required, kind, name):
        target(environment.host)
        target(client.base_url)
        headers = {"Authorization": "Bearer " + token} if token else {}
        failure = None
        body = None
        with client.request(method, path, json=payload, headers=headers, name=name or path.split("?")[0],
                            catch_response=True, allow_redirects=False, timeout=20) as response:
            try:
                body = response.json() if response.content else None
            except ValueError:
                pass
            try:
                validate(response.status_code, body, expected, required, kind)
            except RequestFailure as error:
                # Only report fixed endpoint labels and status, never server bodies/tokens.
                failure = RequestFailure(f"{method} {name or path.split('?')[0]}: {error}",
                                         response.status_code or None)
                response.failure(str(failure))
            else:
                response.success()
        if failure:
            raise failure
        return None if response.status_code == 404 else body

    return send


class Visitor(HttpUser):
    weight = 60
    host = BASE_URL
    wait_time = between(2, 8)
    prefer_provider = None

    def on_start(self):
        self.account = None
        self.business, self.items = None, []
        self.counted = False
        if state is None or clusters is None or not state.data["initialized"]:
            raise StopUser()
        with state.mutex:
            if state.active_users >= 100:
                fail_run(self.environment, "More than 100 virtual users requested")
                raise StopUser()
            state.active_users += 1
            self.counted = True
        self.api = Api(transport_for(self.environment, self.client))
        if self.prefer_provider is not None:
            self.perform(lambda: self.acquire_account(self.prefer_provider))

    def on_stop(self):
        if state:
            state.release(self.account)
        if self.counted:
            with state.mutex:
                state.active_users -= 1

    def acquire_account(self, provider=False):
        account = state.lease(provider)
        if account:
            self.account = account
            self.api.login(account)
            self.business, self.items = reconcile(self.api, state, account)

    def browse(self):
        business_search = random.random() < 0.5
        endpoint = "/api/v1/businesses" if business_search else "/api/v1/items"
        params = {"page": 0, "size": 10, "sort": random.choice(["name,asc", "name,desc"])}
        if random.random() < 0.45:
            cluster = random.choice(clusters)
            params["q"] = cluster["name"].split()[0] if business_search else random.choice(cluster["items"])["title"].split()[0]
        if random.random() < 0.25:
            params["cityId"] = random.choice(list(catalogs["city"].values()))
        if random.random() < 0.25:
            params["categoryIds"] = random.choice(list(catalogs["business" if business_search else "item"].values()))
        if not business_search and random.random() < 0.3:
            params.update(type=random.choice(["PRODUCT", "SERVICE"]), minPrice=0, maxPrice=10000)
        results = self.api.call("GET", endpoint + "?" + urlencode(params), required=("content", "totalPages"))
        if not isinstance(results["content"], list):
            raise SimulationError("Invalid discovery page")
        if not results["content"]:
            return
        selected = random.choice(results["content"])
        if business_search:
            name = selected["uniqueName"]
            self.api.call("GET", f"/api/v1/businesses/{name}", expected=(200, 404),
                          required=("id", "information"), name="/api/v1/businesses/{uniqueName}")
            self.api.call("GET", f"/api/v1/businesses/{name}/items?page=0&size=10",
                          required=("content",), name="/api/v1/businesses/{uniqueName}/items")
        else:
            self.api.call("GET", f"/api/v1/items/{selected['id']}", expected=(200, 404),
                          required=("id", "title"), name="/api/v1/items/{id}")

    def registration(self):
        if self.account:
            return
        # Reserve and lease without a yielding HTTP request between them.
        with state.mutex:
            account = state.new_account(people, random.randrange(len(clusters)))
            if account:
                state.leased.add(account["sequence"])
                self.account = account
        if account:
            register(self.api, state, account)
            self.api.login(account)
        else:
            self.acquire_account()

    def manage(self):
        if not self.account:
            self.registration()
            return
        cluster = clusters[self.account["cluster"]]
        if not self.business:
            self.business = create_business(self.api, state, self.account, cluster)
            if self.business:
                self.items = []
            return
        action = random.randrange(100)
        active = sum(item["status"] == "ACTIVE" for item in self.items)
        if action < 30:
            if len(self.items) < ITEM_LIMIT:
                self.items.append(create_item(self.api, state, self.account, random.choice(cluster["items"])))
            return
        if action < 60 and self.items:
            item = random.choice(self.items)
            template = next((entry for entry in cluster["items"] if entry["title"] == item["title"]), None)
            if template is None:
                raise SimulationError("Owned item does not match its curated cluster")
            patch = random.choice([
                {"price": random.randint(*template["price"])},
                {"description": template["description"] + " Contact us for availability."},
                {"status": "INACTIVE" if item["status"] == "ACTIVE" else "ACTIVE"},
            ])
            if (self.account["protected"] and active <= 2 and item["status"] == "ACTIVE"
                    and patch.get("status") == "INACTIVE"):
                return
            self.mark_pending("items")
            updated = self.api.call("PATCH", f"/api/v1/items/{item['id']}", patch, auth=True,
                                    required=("id", "status", "title", "type", "price"), name="/api/v1/items/{id}")
            self.items[self.items.index(item)] = updated
            self.clear_pending()
        elif action < 80:
            information = {key: self.business["information"].get(key) for key in EDITABLE_INFORMATION}
            information["description"] = cluster["description"] + random.choice([
                " Appointments available this week.", " Contact us for current availability."])
            self.mark_pending("business")
            self.business = self.api.call("PUT", "/api/v1/businesses/me", information, auth=True,
                                          required=("id", "uniqueName", "information", "status"))
            self.clear_pending()
        elif action < 99 and self.items:
            choices = [item for item in self.items if not self.account["protected"]
                       or item["status"] != "ACTIVE" or active > 2]
            if choices:
                item = random.choice(choices)
                self.mark_pending("items")
                self.api.call("DELETE", f"/api/v1/items/{item['id']}", auth=True,
                              expected=(204,), name="/api/v1/items/{id}")
                self.items.remove(item)
                self.clear_pending()
        elif action == 99 and not self.account["protected"]:
            self.mark_pending("delete")
            self.api.call("DELETE", "/api/v1/businesses/me", auth=True, expected=(204,))
            self.account["business"] = None
            self.business, self.items = None, []
            self.clear_pending()
            self.api.refresh()

    def mark_pending(self, operation):
        self.account["pending"] = operation
        state.save()

    def clear_pending(self):
        self.account["pending"] = None
        state.save()

    @task(95)
    def read_activity(self):
        self.perform(self.browse)

    @task(5)
    def write_activity(self):
        self.perform(self.manage)

    def perform(self, operation):
        try:
            operation()
        except RequestFailure as error:
            log.warning("Action failed: %s", error)
            if self.account:
                try:
                    if self.account["pending"] == "register":
                        self.api.login(self.account)
                    self.business, self.items = reconcile(self.api, state, self.account)
                except SimulationError:
                    fail_run(self.environment, "Cannot reconcile an account after a failed operation; inspect runtime state")
                    raise StopUser() from None
        except (SimulationError, KeyError, TypeError, ValueError):
            fail_run(self.environment, "Invalid response or runtime state; inspect simulation data and API compatibility")
            raise StopUser() from None


class Customer(Visitor):
    weight = 25
    prefer_provider = False


class Provider(Visitor):
    weight = 15
    prefer_provider = True
