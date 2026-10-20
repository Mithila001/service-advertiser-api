"""Small deterministic checks; optional --live exercises the real HTTP lifecycle."""

import importlib.util

HAS_LOCUST = importlib.util.find_spec("locust") is not None
if HAS_LOCUST:
    import locust  # Apply its usual gevent patches before importing HTTP helpers.

import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import patch
from types import SimpleNamespace

from common import (ACCOUNT_LIMIT, BUSINESS_LIMIT, Api, HttpTransport, RequestFailure,
                    SimulationError, State, create_business, create_item, identity,
                    load_catalogs, load_data, reconcile, register, target, validate)


def tokens(access="first_access", refresh="first_refresh"):
    return {"accessToken": access, "refreshToken": refresh, "tokenType": "Bearer", "expiresIn": 900,
            "refreshTokenExpiresAt": (datetime.now(timezone.utc) + timedelta(days=1)).isoformat()}


class FakeTransport:
    def __init__(self, *steps):
        self.steps = list(steps)
        self.calls = []

    def __call__(self, method, path, payload, token, expected, required, kind, name):
        self.calls.append((method, path, payload, token))
        status, body = self.steps.pop(0)
        validate(status, body, expected, required, kind)
        return None if status == 404 else body


class Checks(unittest.TestCase):
    def test_catalog_pagination_and_required_mapping(self):
        transport = FakeTransport((200, [{"id": 1, "name": "IT & Technology"}]), (200, []),
                                  (200, {"content": [{"id": 2, "name": "Colombo", "districtName": "Colombo"}], "totalPages": 2}),
                                  (200, {"content": [{"id": 3, "name": "Kandy", "districtName": "Kandy"}], "totalPages": 2}))
        catalogs = load_catalogs(Api(transport))
        self.assertEqual(catalogs["city"][("Kandy", "Kandy")], 3)
        self.assertIn("page=1", transport.calls[-1][1])
        with self.assertRaisesRegex(SimulationError, "Missing required"):
            load_data(catalogs)

    def test_all_curated_templates_resolve(self):
        import json
        from common import ROOT
        clusters = json.loads((ROOT / "data/businesses.json").read_text())
        catalogs = {"business": {}, "item": {}, "city": {}}
        for cluster in clusters:
            for name in cluster["categories"]:
                catalogs["business"][name] = len(catalogs["business"]) + 1
            for city in cluster["locations"]:
                catalogs["city"][tuple(city)] = len(catalogs["city"]) + 1
            for item in cluster["items"]:
                for name in item["categories"]:
                    catalogs["item"][name] = len(catalogs["item"]) + 1
        people, resolved = load_data(catalogs)
        self.assertTrue(people)
        self.assertEqual(len(resolved), 20)
        self.assertEqual({i["type"] for c in resolved for i in c["items"]}, {"PRODUCT", "SERVICE"})

    def test_rotation_expiry_and_lost_refresh(self):
        transport = FakeTransport((200, tokens()), (200, tokens("next_access", "next_refresh")),
                                  (401, {}), (200, tokens("relogin_access", "relogin_refresh")),
                                  (200, tokens("expired_access", "expired_refresh")))
        api = Api(transport)
        api.login(identity(1))
        api.access_deadline = 0
        api.ensure_session()
        self.assertEqual(api.refresh_token, "next_refresh")
        api.refresh()
        self.assertEqual(api.refresh_token, "relogin_refresh")
        api.refresh_deadline = 0
        api.refresh()
        self.assertEqual(transport.calls[-1][1], "/api/v1/auth/login")
        used = [call[2]["refreshToken"] for call in transport.calls if call[1].endswith("/refresh")]
        self.assertEqual(used, ["first_refresh", "next_refresh"])
        api.transport = FakeTransport((200, tokens("lost_access", "lost_refresh")))
        original = api.transport

        def lose_once(*args):
            if args[1].endswith("/refresh"):
                raise RequestFailure("transport failure")
            return original(*args)

        api.transport = lose_once
        api.refresh()
        self.assertEqual(api.refresh_token, "lost_refresh")
        with self.assertRaises(RequestFailure):
            api.accept_tokens({"accessToken": "secret"})

    def test_leases_caps_and_bootstrap_refusal(self):
        with tempfile.TemporaryDirectory() as directory:
            state = State(directory)
            state.require_fresh()
            first = state.new_account(["Example Person"], 0)
            first["pending"] = None
            second = state.new_account(["Another Person"], 1)
            second.update(pending=None, business={"id": 10})
            self.assertIs(state.lease(provider=True), second)
            self.assertIs(state.lease(provider=True), first)
            self.assertIsNone(state.lease())
            state.release(first)
            self.assertIs(state.lease(), first)
            with self.assertRaises(SimulationError):
                state.require_fresh()
            # Synthetic in-memory fill tests capacity without 500 filesystem writes.
            state.data["accounts"] += [{"business": None}] * (ACCOUNT_LIMIT - 2)
            self.assertIsNone(state.new_account(["Example Person"], 0))
            state.data["accounts"] = [{"business": {"id": i}} for i in range(BUSINESS_LIMIT - 1)]
            self.assertIsNotNone(state.reserve_business())
            self.assertIsNone(state.reserve_business())
            state.finish_reservation()
            self.assertIsNotNone(state.reserve_business())
            state.finish_reservation()
            state.data["accounts"].append({"business": None, "pending": "create_business"})
            self.assertIsNone(state.reserve_business())

    def test_process_lock_and_manifest_corruption(self):
        with tempfile.TemporaryDirectory() as directory:
            first, second = State(directory), State(directory)
            first.lock()
            try:
                with self.assertRaises(SimulationError):
                    second.lock()
            finally:
                first.close()
            first.path.write_text("{broken")
            with self.assertRaises(SimulationError):
                State(directory)

    def test_creation_refresh_and_failed_write_reconciliation(self):
        with tempfile.TemporaryDirectory() as directory:
            state = State(directory)
            account = state.new_account(["Example Person"], 0)
            api = Api(FakeTransport((201, {"id": 42, "role": "CUSTOMER"}), (200, tokens()),
                                    (201, {"id": 8, "uniqueName": "sim_test_000001", "information": {}, "status": "ACTIVE"}),
                                    (200, tokens("provider_access", "provider_refresh")),
                                    (409, {}), (200, {"id": 8, "uniqueName": "sim_test_000001", "information": {}, "status": "ACTIVE"}),
                                    (200, []), (204, None), (200, tokens("customer_access", "customer_refresh")),
                                    (404, {"status": 404})))
            register(api, state, account)
            api.login(account)
            cluster = {"base": "test", "name": "Example", "description": "Example", "address": "1 Lane", "cityIds": [1], "categoryIds": [1]}
            create_business(api, state, account, cluster)
            self.assertEqual(api.access, "provider_access")
            self.assertEqual(account["business"]["id"], 8)
            template = {"title": "Example", "description": "Example", "type": "SERVICE", "categoryIds": [1], "price": [10, 20]}
            with self.assertRaises(RequestFailure):
                create_item(api, state, account, template)
            self.assertEqual(account["pending"], "items")
            business, items = reconcile(api, state, account)
            self.assertEqual(items, [])
            self.assertIsNone(account["pending"])
            api.call("DELETE", "/api/v1/businesses/me", auth=True, expected=(204,))
            api.refresh()
            business, items = reconcile(api, state, account)
            self.assertIsNone(business)
            self.assertIsNone(account["business"])
            self.assertEqual(api.access, "customer_access")
            self.assertEqual(state.business_reservations, 0)

    def test_target_and_response_guards(self):
        for host in ("http://localhost:8080", "http://api:8080.evil", "http://api:8080/"):
            with self.assertRaises(SimulationError):
                target(host)
        with self.assertRaises(RequestFailure):
            validate(302, {}, (200,))
        with self.assertRaises(RequestFailure):
            validate(200, {}, (200,), ("id",))

    @unittest.skipUnless(HAS_LOCUST, "Run inside the Locust image for dashboard checks")
    def test_dashboard_and_runner_guards(self):
        from locust.env import Environment
        import locustfile as workload
        from common import BASE_URL
        with tempfile.TemporaryDirectory() as directory:
            runtime = State(directory)
            environment = Environment(host=BASE_URL)
            environment.create_local_runner()
            environment.create_web_ui("127.0.0.1", 0, delayed_start=True)
            with patch.object(workload, "State", return_value=runtime):
                workload.initialize(environment)
            try:
                with patch.object(workload, "fail_run") as stopped:
                    environment.runner.start(101, 1)
                    stopped.assert_called_once()
                with patch.object(workload, "fail_run") as stopped:
                    environment.runner.start(1, 1)
                    stopped.assert_called_once()
                web = environment.web_ui.app.test_client()
                self.assertEqual(web.post("/swarm", data={"user_count": "101", "spawn_rate": "1", "host": BASE_URL}).status_code, 400)
                self.assertEqual(web.post("/swarm", data={"user_count": "10", "spawn_rate": "1", "host": "http://localhost:8080"}).status_code, 400)
                self.assertEqual(environment.host, BASE_URL)
            finally:
                runtime.close()
                environment.runner.quit()

    @unittest.skipUnless(HAS_LOCUST, "Run inside the Locust image for workload checks")
    def test_protected_business_and_active_item_floor(self):
        import locustfile as workload
        user = SimpleNamespace(account={"cluster": 0, "protected": True}, business={"id": 1},
                               items=[{"id": 1, "title": "Test", "status": "ACTIVE"},
                                      {"id": 2, "title": "Test", "status": "ACTIVE"}])
        user.api = SimpleNamespace(call=lambda *a, **k: self.fail("Protected floor allowed a mutation"))
        cluster = {"items": [{"title": "Test", "price": [1, 10], "description": "Test"}]}
        with patch.object(workload, "clusters", [cluster]):
            for action in (30, 80, 99):
                with patch.object(workload.random, "randrange", return_value=action), patch.object(workload.random, "choice", side_effect=lambda values: values[-1]):
                    workload.Visitor.manage(user)


def live():
    """Leaves one synthetic customer account; removes only its own new business."""
    state = State()
    state.lock()
    try:
        if not state.data["initialized"]:
            raise SimulationError("Bootstrap must complete before --live")
        api = Api(HttpTransport())
        people, clusters = load_data(load_catalogs(api))
        account = state.new_account(people, 0)
        if account is None:
            raise SimulationError("Account cap reached; run lifecycle check after reset/bootstrap")
        register(api, state, account)
        api.login(account)
        assert role(api) == "CUSTOMER"
        business = create_business(api, state, account, clusters[0])
        if not business:
            raise SimulationError("Business cap reached")
        assert role(api) == "PROVIDER"
        item = create_item(api, state, account, clusters[0]["items"][0])
        api.call("PATCH", f"/api/v1/items/{item['id']}", {"price": 1234.50, "status": "INACTIVE"}, auth=True,
                 required=("id", "status", "price"))
        api.call("DELETE", f"/api/v1/items/{item['id']}", auth=True, expected=(204,))
        api.call("DELETE", "/api/v1/businesses/me", auth=True, expected=(204,))
        api.refresh()
        assert role(api) == "CUSTOMER"
        business, items = reconcile(api, state, account)
        assert business is None and items == []
        create_business(api, state, account, clusters[0])
        api.call("DELETE", "/api/v1/businesses/me", auth=True, expected=(204,))
        api.refresh()
        reconcile(api, state, account)
        print("Live lifecycle passed: registration, login, promotion, item mutations, deletion, demotion, re-registration.")
    finally:
        state.close()


def role(api):
    # Inspect only this test's server-issued JWT; this is not JWT authentication.
    import base64
    import json
    payload = api.access.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))["role"]


if __name__ == "__main__":
    if "--live" in sys.argv:
        try:
            live()
        except SimulationError as error:
            print(f"Live check stopped: {error}", file=sys.stderr)
            sys.exit(1)
    else:
        unittest.main()
