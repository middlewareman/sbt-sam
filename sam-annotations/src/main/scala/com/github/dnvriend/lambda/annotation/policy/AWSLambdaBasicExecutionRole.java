/*
 * Copyright 2017 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.dnvriend.lambda.annotation.policy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Grants permissions only for the Amazon CloudWatch Logs actions to write logs. You can use this policy if your Lambda function does not access any other AWS resources except writing logs.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface AWSLambdaBasicExecutionRole {
}
