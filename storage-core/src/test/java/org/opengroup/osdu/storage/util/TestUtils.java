// Copyright 2017-2021, Schlumberger
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

package org.opengroup.osdu.storage.util;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;

public class TestUtils {

  public static Matcher<AppException> buildAppExceptionMatcher(String message, String reason) {
    return new Matcher<AppException>() {
      @Override
      public boolean matches(Object o) {
        AppException appException = (AppException) o;
        AppError error = appException.getError();

        return error.getMessage().equals(message) && error.getReason().equals(reason);
      }

      @Override
      public void describeMismatch(Object o, Description description) {

      }

      @Override
      public void _dont_implement_Matcher___instead_extend_BaseMatcher_() {

      }

      @Override
      public void describeTo(Description description) {

      }
    };
  }
}