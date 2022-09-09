import json
import logging
import os
import threading
from typing import Iterable

import tenacity
from config_manager import ConfigManager
from prepare_records import RecordsPreparer
from osdu_api.clients.storage.record_client import RecordClient
from osdu_api.model.storage.record import Record
from osdu_ingestion.libs.refresh_token import BaseTokenRefresher
from utils import prepared_manifests_records

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

error_report = []

# get optional environmental variables
try:
    THREAD_NUMBER = int(os.environ.get("THREAD_NUMBER", 3))
except ValueError as e:
    logger.error(
        f"Environmental variable THREAD_NUMBER must be type of integer.")
    raise e

# get environmental variables
try:
    ACL_OWNERS = json.loads(os.environ["ACL_OWNERS"])
    ACL_VIEWERS = json.loads(os.environ["ACL_VIEWERS"])
    LEGALTAGS = json.loads(os.environ["LEGALTAGS"])
    MANIFESTS_DIR = os.environ["MANIFESTS_DIR"]
except (KeyError, json.JSONDecodeError) as e:
    logger.error(
        "The following env variable must be set:\n"
        "\tACL_OWNERS='[\"value_1\", \"value_2\"]'\n"
        "\tACL_VIEWERS='[\"value_1\", \"value_2\"]'\n"
        "\tLEGALTAGS='[\"value_1\", \"value_2\"]'\n"
        "\tMANIFESTS_DIR='/path/to/manifests'\n"
    )
    raise e


def on_storage_error_callback(retry_state: tenacity.RetryCallState):
    try:
        retry_state.outcome.result()
    except Exception as e:
        print(e)



class DeleteFromStorageThread(threading.Thread):

    _lock = threading.Lock()

    def __init__(self, storage_client: RecordClient, records: Iterable[Record]):
        super().__init__()
        self._storage_client = storage_client
        self._records = records

    @tenacity.retry(
        wait=tenacity.wait_fixed(1),
        stop=tenacity.stop_after_attempt(1),
        retry_error_callback=on_storage_error_callback,
        reraise=True
    )
    def send_storage_request(self, record: Record):
        print(f"Delete record {record.id}")
        self._storage_client.delete_record(record.id)

    def run(self):
        while True:
            try:
                self._lock.acquire()
                record = next(self._records)
                self._lock.release()
                self._send_storage_request(record)
            except StopIteration:
                logger.info("There are no records left to save.")
                self._lock.release()
                break

def main():
    config_manager = ConfigManager()
    records_preparer = RecordsPreparer(
        data_partition_id=config_manager.get(
            "environment", "data_partition_id"),
        acl_owners=ACL_OWNERS,
        acl_viewers=ACL_VIEWERS,
        legaltags=LEGALTAGS
    )

    token_refresher = BaseTokenRefresher()
    token_refresher.refresh_token()

    storage_client = RecordClient(
        config_manager=config_manager, token_refresher=token_refresher)

    manifests_records = prepared_manifests_records(
        records_preparer, MANIFESTS_DIR)

    threads = []
    for _ in THREAD_NUMBER:
        threads.append(DeleteFromStorageThread(storage_client, manifests_records))

    for t in threads:
        t.start()
    for t in threads:
        t.join()
        

if __name__ == "__main__":
    main()
