"""Shared valid-client behavior; no application-domain database access."""

import atexit
import json
import os
from pathlib import Path
import random
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

BASE_URL = "http://api:8080"
ROOT = Path(__file__).resolve().parent
ACCOUNT_LIMIT = 500
BUSINESS_LIMIT = 100
ITEM_LIMIT = 10
EDITABLE_INFORMATION = (
    "name", "description", "phone", "email", "websiteUrl", "addressLine1",
    "addressLine2", "cityId", "postalCode", "latitude", "longitude", "logoUrl", "categoryIds",
)


def identity(sequence):
    # Deliberately predictable, synthetic credentials also detect a lost manifest.
    return {"email": f"sim_user_{sequence:04d}@example.test",
            "password": f"Simulation_only_{sequence:04d}_Example!"}


class SimulationError(Exception):
    """Safe to display: contains no request/response bodies or credentials."""


class RequestFailure(SimulationError):
    def __init__(self, message, status=None):
        super().__init__(message)
        self.status = status


def validate(status, body, expected, required=(), kind=dict):
    if status not in expected:
        raise RequestFailure(f"unexpected HTTP {status}", status)
    if status == 204 or status == 404:
        return
    if not isinstance(body, kind):
        raise RequestFailure("invalid JSON response shape", status)
    if kind is dict and any(key not in body for key in required):
        raise RequestFailure("response is missing required fields", status)


def target(host):
    if host != BASE_URL:
        raise SimulationError(f"Only the isolated Compose target {BASE_URL} is allowed")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class HttpTransport:
    def __init__(self):
        # Ignore host proxy settings: this hostname is private to Compose.
        self.opener = build_opener(ProxyHandler({}), NoRedirect())

    def __call__(self, method, path, payload, token, expected, required, kind, name):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = Request(BASE_URL + path, method=method, headers=headers,
                          data=None if payload is None else json.dumps(payload).encode())
        try:
            try:
                response = self.opener.open(request, timeout=20)
            except HTTPError as error:
                response = error
            with response:
                status = response.code
                raw = response.read()
            try:
                body = json.loads(raw) if raw else None
            except (ValueError, UnicodeError):
                body = None
            try:
                validate(status, body, expected, required, kind)
            except RequestFailure as error:
                raise RequestFailure(f"{method} {name or path.split('?')[0]}: {error}", status) from None
            return None if status == 404 else body
        except (URLError, OSError, TimeoutError):
            raise RequestFailure(f"{method} {name or path}: transport failure; outcome may be uncertain") from None


class Api:
    def __init__(self, transport):
        self.transport = transport
        self.access = self.refresh_token = None
        self.access_deadline = self.refresh_deadline = 0
        self.account = None

    def call(self, method, path, payload=None, *, auth=False, expected=(200,),
             required=(), kind=dict, name=None):
        if not path.startswith("/api/v1/") or "://" in path or "\\" in path:
            raise SimulationError("Unsafe API path")
        if auth:
            self.ensure_session()
        return self.transport(method, path, payload, self.access if auth else None,
                              expected, required, kind, name)

    def accept_tokens(self, body):
        from datetime import datetime, timezone
        try:
            lifetime = float(body["expiresIn"])
            expiry = datetime.fromisoformat(body["refreshTokenExpiresAt"].replace("Z", "+00:00"))
            remaining = (expiry - datetime.now(timezone.utc)).total_seconds()
            if (body["tokenType"] != "Bearer" or lifetime <= 0 or remaining <= 0
                    or not isinstance(body["accessToken"], str) or not body["accessToken"]
                    or not isinstance(body["refreshToken"], str) or not body["refreshToken"]):
                raise ValueError()
        except (KeyError, ValueError, TypeError, AttributeError):
            raise RequestFailure("Invalid authentication response") from None
        self.access, self.refresh_token = body["accessToken"], body["refreshToken"]
        now = time.monotonic()
        self.access_deadline = now + lifetime - min(10, lifetime / 5)
        self.refresh_deadline = now + remaining

    def login(self, account):
        self.account = account
        body = self.call("POST", "/api/v1/auth/login",
                         {"email": account["email"], "password": account["password"]},
                         required=("accessToken", "refreshToken", "expiresIn", "refreshTokenExpiresAt", "tokenType"))
        self.accept_tokens(body)

    def refresh(self):
        if not self.refresh_token or time.monotonic() >= self.refresh_deadline:
            self.login(self.account)
            return
        old_token = self.refresh_token
        # Never retry this token, even if the server's response is lost.
        self.refresh_token = None
        try:
            body = self.call("POST", "/api/v1/auth/refresh", {"refreshToken": old_token},
                             required=("accessToken", "refreshToken", "expiresIn", "refreshTokenExpiresAt", "tokenType"))
            self.accept_tokens(body)
        except RequestFailure as error:
            if error.status in (None, 401):
                self.login(self.account)
            else:
                raise

    def ensure_session(self):
        if not self.account:
            raise SimulationError("Protected request has no leased account")
        if not self.access or time.monotonic() >= self.access_deadline:
            self.refresh()

    def ownership(self):
        business = self.call("GET", "/api/v1/businesses/me", auth=True,
                             expected=(200, 404), required=("id", "uniqueName", "information", "status"))
        if business and business.get("status") != "ACTIVE":
            raise SimulationError("Simulation business is not ACTIVE; reset or inspect the environment")
        items = self.call("GET", "/api/v1/businesses/me/items", auth=True, kind=list) if business else []
        if any(not isinstance(item, dict) or not {"id", "status", "title", "type", "price"} <= item.keys()
               for item in items):
            raise SimulationError("Invalid owned item response")
        return business, items


def load_catalogs(api):
    def categories(path):
        rows = api.call("GET", path, kind=list)
        if any(not isinstance(row, dict) or not {"id", "name"} <= row.keys() for row in rows):
            raise SimulationError("Invalid category catalog")
        return {row["name"]: row["id"] for row in rows}

    catalogs = {"business": categories("/api/v1/categories/business"),
                "item": categories("/api/v1/categories/item"), "city": {}}
    page = 0
    while True:
        body = api.call("GET", f"/api/v1/locations/cities?page={page}&size=100",
                        required=("content", "totalPages"), name="/api/v1/locations/cities")
        if not isinstance(body["content"], list) or not isinstance(body["totalPages"], int):
            raise SimulationError("Invalid city page")
        for city in body["content"]:
            try:
                key = (city["name"], city["districtName"])
                if key in catalogs["city"]:
                    raise SimulationError("Ambiguous city catalog entry")
                catalogs["city"][key] = city["id"]
            except (KeyError, TypeError):
                raise SimulationError("Invalid city catalog entry") from None
        page += 1
        if page >= body["totalPages"]:
            break
        if page > 100:
            raise SimulationError("City pagination did not terminate")
    return catalogs


def load_data(catalogs):
    people = json.loads((ROOT / "data/people.json").read_text(encoding="utf-8"))
    clusters = json.loads((ROOT / "data/businesses.json").read_text(encoding="utf-8"))
    if not people or not all(isinstance(name, str) and 0 < len(name) <= 150 for name in people):
        raise SimulationError("Invalid synthetic people")
    if len(clusters) != 20:
        raise SimulationError("Expected 20 curated business clusters")
    try:
        for cluster in clusters:
            cluster["categoryIds"] = [catalogs["business"][name] for name in cluster["categories"]]
            cluster["cityIds"] = [catalogs["city"][tuple(city)] for city in cluster["locations"]]
            if not (1 <= len(cluster["categoryIds"]) <= 3
                    and len(set(cluster["categoryIds"])) == len(cluster["categoryIds"]) and cluster["cityIds"]
                    and 3 <= len(cluster["items"]) <= 5 and len(cluster["base"]) <= 25
                    and cluster["base"].isascii()
                    and all(c.islower() or c.isdigit() or c == "_" for c in cluster["base"])
                    and 0 < len(cluster["name"]) <= 130):
                raise ValueError()
            for item in cluster["items"]:
                item["categoryIds"] = [catalogs["item"][name] for name in item["categories"]]
                if not (item["type"] in ("PRODUCT", "SERVICE") and 0 < len(item["title"]) <= 150
                        and 1 <= len(item["categoryIds"]) <= 2
                        and len(set(item["categoryIds"])) == len(item["categoryIds"])
                        and 0 <= item["price"][0] <= item["price"][1]):
                    raise ValueError()
    except KeyError as error:
        raise SimulationError(f"Missing required catalog/data entry: {error.args[0]}") from None
    except (ValueError, TypeError, IndexError):
        raise SimulationError("Invalid curated business/item template") from None
    return people, clusters


class State:
    def __init__(self, directory=None):
        self.directory = Path(directory) if directory else ROOT / "runtime"
        self.directory.mkdir(parents=True, exist_ok=True)
        self.path = self.directory / "manifest.json"
        self.file_lock = None
        # ponytail: one process and <=500 accounts; use a transactional store if scaled.
        self.mutex = threading.RLock()
        self.leased = set()
        self.active_users = 0
        self.business_reservations = 0
        try:
            self.data = json.loads(self.path.read_text(encoding="utf-8")) if self.path.exists() else {
                "version": 1, "initialized": False, "next_account": 1, "next_business": 1, "accounts": []}
            if (self.data["version"] != 1 or not isinstance(self.data["accounts"], list)
                    or len(self.data["accounts"]) > ACCOUNT_LIMIT
                    or not isinstance(self.data["initialized"], bool)
                    or not isinstance(self.data["next_account"], int)
                    or not isinstance(self.data["next_business"], int) or self.data["next_business"] < 1):
                raise ValueError()
            ids = [account["sequence"] for account in self.data["accounts"]]
            if len(ids) != len(set(ids)) or self.data["next_account"] <= max(ids, default=0):
                raise ValueError()
            for account in self.data["accounts"]:
                if not {"email", "password", "name", "cluster", "protected", "business", "pending"} <= account.keys():
                    raise ValueError()
                if (not isinstance(account["sequence"], int) or account["sequence"] < 1
                        or account["email"] != identity(account["sequence"])["email"]
                        or not isinstance(account["password"], str) or not account["password"]
                        or not isinstance(account["cluster"], int) or not 0 <= account["cluster"] < 20
                        or not isinstance(account["protected"], bool)
                        or account["pending"] not in (None, "register", "create_business", "business", "items", "delete")):
                    raise ValueError()
                business = account["business"]
                if business is not None and (not isinstance(business, dict) or not {"id", "uniqueName"} <= business.keys()):
                    raise ValueError()
            if self.data["initialized"] and (len(ids) < 100 or sum(a["protected"] for a in self.data["accounts"]) != 20):
                raise ValueError()
        except (ValueError, KeyError, TypeError, OSError):
            raise SimulationError("Runtime manifest is invalid; inspect it or explicitly reset simulation data") from None

    def lock(self):
        # Runtime is Linux inside Docker; msvcrt also permits the checks on Windows.
        self.file_lock = (self.directory / "process.lock").open("a+b")
        try:
            if os.name == "nt":
                import msvcrt
                self.file_lock.seek(0)
                self.file_lock.write(b"0")
                self.file_lock.flush()
                self.file_lock.seek(0)
                msvcrt.locking(self.file_lock.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.file_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            self.file_lock.close()
            raise SimulationError("Another bootstrap/Locust process owns this runtime") from None
        atexit.register(self.close)

    def close(self):
        if self.file_lock and not self.file_lock.closed:
            self.file_lock.close()

    def save(self):
        with self.mutex:
            temporary = self.path.with_suffix(".tmp")
            with temporary.open("w", encoding="utf-8") as output:
                json.dump(self.data, output, indent=2)
                output.flush()
                os.fsync(output.fileno())
            os.chmod(temporary, 0o600)
            temporary.replace(self.path)

    def require_fresh(self):
        if self.path.exists() or self.data["accounts"]:
            raise SimulationError("Bootstrap already attempted; use the documented explicit reset")

    def new_account(self, people, cluster, protected=False):
        with self.mutex:
            if len(self.data["accounts"]) >= ACCOUNT_LIMIT:
                return None
            sequence = self.data["next_account"]
            self.data["next_account"] += 1
            account = {"sequence": sequence, "name": random.choice(people),
                       **identity(sequence), "cluster": cluster,
                       "protected": protected, "business": None, "pending": "register"}
            self.data["accounts"].append(account)
            self.save()  # Reserve identity before HTTP; never lose a successful registration.
            return account

    def lease(self, provider=False):
        with self.mutex:
            available = [a for a in self.data["accounts"] if a["sequence"] not in self.leased and not a["pending"]]
            preferred = [a for a in available if bool(a["business"]) == provider]
            if not available:
                return None
            account = random.choice(preferred or available)
            self.leased.add(account["sequence"])
            return account

    def release(self, account):
        with self.mutex:
            if account:
                self.leased.discard(account["sequence"])

    def reserve_business(self):
        with self.mutex:
            # Unknown create outcomes keep a slot until ownership is reconciled.
            count = sum(bool(a["business"]) or a.get("pending") == "create_business"
                        for a in self.data["accounts"])
            if count + self.business_reservations >= BUSINESS_LIMIT:
                return None
            self.business_reservations += 1
            sequence = self.data["next_business"]
            self.data["next_business"] += 1
            self.save()
            return sequence

    def finish_reservation(self):
        with self.mutex:
            self.business_reservations -= 1


def register(api, state, account):
    body = api.call("POST", "/api/v1/auth/register",
                    {**{key: account[key] for key in ("name", "email", "password")}, "phone": "+94771234567"},
                    expected=(201,), required=("id", "role"))
    if body["role"] != "CUSTOMER":
        raise SimulationError("Registration did not return CUSTOMER")
    account.update(id=body["id"], pending=None)
    state.save()


def reconcile(api, state, account):
    business, items = api.ownership()
    account["business"] = {"id": business["id"], "uniqueName": business["uniqueName"]} if business else None
    account["pending"] = None
    state.save()
    return business, items


def create_business(api, state, account, cluster):
    sequence = state.reserve_business()
    if sequence is None:
        return None
    try:
        account["pending"] = "create_business"
        state.save()
        information = {"name": cluster["name"], "description": cluster["description"],
                       "phone": "+94110000000", "email": account["email"],
                       "addressLine1": cluster["address"], "cityId": random.choice(cluster["cityIds"]),
                       "categoryIds": cluster["categoryIds"]}
        business = api.call("POST", "/api/v1/businesses",
                            {"uniqueName": f"sim_{cluster['base']}_{sequence:06d}", "information": information},
                            auth=True, expected=(201,), required=("id", "uniqueName", "information", "status"))
        account.update(business={"id": business["id"], "uniqueName": business["uniqueName"]}, pending=None)
        state.save()
        api.refresh()
        return business
    finally:
        state.finish_reservation()


def item_payload(template):
    return {"title": template["title"], "description": template["description"], "type": template["type"],
            "categoryIds": template["categoryIds"], "price": random.randint(*template["price"])}


def create_item(api, state, account, template):
    account["pending"] = "items"
    state.save()
    item = api.call("POST", "/api/v1/businesses/me/items", item_payload(template), auth=True,
                    expected=(201,), required=("id", "title", "status", "type", "price"))
    account["pending"] = None
    state.save()
    return item
