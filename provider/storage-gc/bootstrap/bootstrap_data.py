import json
import logging
import os
import threading
from typing import Iterable, List

import requests
import tenacity
from config_manager import ConfigManager
from prepare_records import RecordsPreparer
from osdu_api.clients.storage.record_client import RecordClient
from osdu_api.model.record import Record
from osdu_ingestion.libs.refresh_token import BaseTokenRefresher
from osdu_ingestion.libs.utils import split_into_batches
from storage_client import StorageClient
from utils import prepared_manifests_records, unique_records

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

error_report = []

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

# get optional environmental variables
try:
    THREAD_NUMBER = int(os.environ.get("THREAD_NUMBER", 3))
    BATCH_SIZE = int(os.environ.get("BATCH_SIZE", 250))
except ValueError as e:
    logger.error(
        f"Environmental variables THREAD_NUMBER and BATCH_SIZE must be type of integer.")
    raise e

def on_storage_error_callback(retry_state: tenacity.RetryCallState):
    failed_record_ids = [record.id for record in retry_state.args[1]]
    try:
        retry_state.outcome.result()
    except (requests.HTTPError, requests.exceptions.ConnectionError) as e:
        error_msg = str(e)
    # TODO: Think about saving dead letters
    error_report.append(
        {
            "error": error_msg,
            "record_ids": failed_record_ids
        }
    )
    return retry_state.outcome.result()


def print_error_report():
    error_report_string = ""
    for err in error_report:
        error_report_string = error_report_string \
            + f"Error: {err['error']}\n Ids: {err['record_ids']}\n"
    if error_report:
        logger.warn(
            "Following records weren't stored: \n"
            f"{error_report_string}"
        )


class SaveToStorageThread(threading.Thread):

    _lock = threading.Lock()

    def __init__(self, storage_client: RecordClient, record_batches: Iterable[List[Record]]):
        super().__init__()
        self._storage_client = storage_client
        self._record_batches = record_batches

    @tenacity.retry(
        wait=tenacity.wait_fixed(3),
        stop=tenacity.stop_after_attempt(3),
        retry_error_callback=on_storage_error_callback,
        reraise=True
    )
    def _send_storage_request(self, record_batch: List[Record]):
        print(f"Send batch of {len(record_batch)} records.")
        response = self._storage_client.create_update_records(record_batch)
        logger.info(response)
        response.raise_for_status()

    def run(self):
        while True:
            try:
                self._lock.acquire()
                record_batch = next(self._record_batches)
                self._lock.release()
                record_batch = unique_records(record_batch)
                self._send_storage_request(record_batch)
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

    storage_client = StorageClient(
        config_manager=config_manager, token_refresher=token_refresher)

    manifests_records = prepared_manifests_records(
        records_preparer, MANIFESTS_DIR)

    record_batches = split_into_batches(
        manifests_records,
        BATCH_SIZE
    )

    threads = []
    for _ in range(THREAD_NUMBER):
        threads.append(SaveToStorageThread(storage_client, record_batches))

    for t in threads:
        t.start()
    for t in threads:
        t.join()

    print_error_report()


if __name__ == "__main__":
    main()
