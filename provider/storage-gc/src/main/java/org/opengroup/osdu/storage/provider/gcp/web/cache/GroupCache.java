/*
 *  Copyright 2020-2023 Google LLC
 *  Copyright 2020-2023 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.web.cache;

import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.entitlements.Groups;

// Group cache is used in common part. According to the current Google Cloud architecture, we don't
// use cache. Thus, methods are empty.
public class GroupCache implements ICache<String, Groups> {

  @Override
  public void put(String s, Groups o) {
    // do nothing
  }

  @Override
  public Groups get(String s) {
    return null;
  }

  @Override
  public void delete(String s) {
    // do nothing
  }

  @Override
  public void clearAll() {
    // do nothing
  }
}
