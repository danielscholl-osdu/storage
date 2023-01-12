FROM google/cloud-sdk:397.0.0-slim

ARG DATA_BRANCH

WORKDIR /opt

ENV MANIFESTS_DIR="./manifests"
ENV DATA_BRANCH=${DATA_BRANCH}

COPY ./provider/storage-gc/bootstrap ./

RUN apt-get update \
    && apt-get install zip jq -y \ 
    && pip3 install --upgrade pip \
    && pip install -r requirements.txt \
    && chmod +x download-data.sh \
    && chmod +x bootstrap_storage.sh \
    && ./download-data.sh 
    
CMD ["/bin/bash", "-c", "/opt/bootstrap_storage.sh && sleep 365d"]
