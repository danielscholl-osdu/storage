import logging
import os
from typing import Iterable, Iterator, List

from osdu_api.model.storage.record import Record
from prepare_records import RecordsPreparer

logger = logging.getLogger()


def manifest_paths(base_dir: str) -> Iterator[str]:
    for root, _, files in os.walk(base_dir):
        for file in files:
            if not file.endswith(".json"):
                continue
            file_path = os.path.join(root, file)
            yield file_path

def unique_records(record_batch: Iterable[Record]) -> List[Record]:
    record_batch = {r.id: r for r in record_batch}.values()
    return list(record_batch)

def prepared_manifests_records(records_preparer: RecordsPreparer, manifests_dir: str) -> Iterator[Record]:
    for file_path in manifest_paths(manifests_dir):
        with open(file_path) as f:
            manifest_string = f.read()
            for record in records_preparer.manifest_records(manifest_string):
                yield record
