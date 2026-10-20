"""One-off population. Run only via the simulation Compose tools service."""

import random
import sys

from common import (Api, HttpTransport, SimulationError, RequestFailure, State,
                    create_business, create_item, identity, load_catalogs, load_data,
                    reconcile, register)


def populate():
    state = State()
    state.lock()
    try:
        state.require_fresh()
        api = Api(HttpTransport())
        people, clusters = load_data(load_catalogs(api))
        try:
            api.login(identity(1))
        except RequestFailure as error:
            if error.status != 401:
                raise
        else:
            raise SimulationError("Simulation identities exist without a manifest; explicitly reset the environment")
        # Also catch a lost manifest with changed credentials but surviving businesses.
        existing = api.call("GET", "/api/v1/businesses?q=Lotus%20IT%20Solutions&size=100",
                            required=("content",))
        if any(row["uniqueName"].startswith("sim_") for row in existing["content"]):
            raise SimulationError("Simulation businesses already exist; explicitly reset the environment")
        state.save()  # An interrupted bootstrap must be explicitly reset.
        total_items = 0
        for index in range(100):
            protected = index < 20
            account = state.new_account(people, index % len(clusters), protected)
            register(api, state, account)
            if protected:
                api.login(account)
                cluster = clusters[account["cluster"]]
                create_business(api, state, account, cluster)
                count = random.randint(3, 4)
                for template in random.sample(cluster["items"], count):
                    create_item(api, state, account, template)
                business, items = reconcile(api, state, account)
                if not business or len(items) != count or any(item["status"] != "ACTIVE" for item in items):
                    raise SimulationError("Bootstrap ownership verification failed")
                public = api.call("GET", f"/api/v1/businesses/{business['uniqueName']}",
                                  required=("id", "uniqueName"))
                if public["id"] != business["id"]:
                    raise SimulationError("Public business verification failed")
                total_items += len(items)
            print(f"Registered {index + 1}/100 accounts", flush=True)
        if (len(state.data["accounts"]) != 100
                or sum(bool(a["business"]) for a in state.data["accounts"]) != 20
                or not 60 <= total_items <= 80):
            raise SimulationError("Bootstrap totals did not match targets")
        state.data["initialized"] = True
        state.data["bootstrap_items"] = total_items
        state.save()
        print(f"Verified 100 accounts (80 customers, 20 providers), 20 businesses, {total_items} items.")
    finally:
        state.close()


if __name__ == "__main__":
    try:
        populate()
    except SimulationError as error:
        print(f"Bootstrap stopped: {error}", file=sys.stderr)
        sys.exit(1)
