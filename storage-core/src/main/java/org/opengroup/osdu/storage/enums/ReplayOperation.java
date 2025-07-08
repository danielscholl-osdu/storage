// Copyright © Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public enum ReplayOperation {

    REINDEX("reindex"),

    REPLAY("replay");

    private final String operation;

    ReplayOperation(String operation) {
        this.operation = operation;
    }

    public static Set<String> getValidReplayOperations() {
        return Arrays.stream(values())
                     .map(ReplayOperation::getOperation)
                     .collect(Collectors.toSet());
    }
}
