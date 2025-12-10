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

RUN groupadd -g 10001 -r nonroot \
  && useradd -d /opt -g 10001 -r -u 10001 nonroot
RUN chown -R 10001:10001 /opt
USER 10001:10001
    
CMD ["/bin/bash", "-c", "/opt/bootstrap_storage.sh && sleep 365d"]
