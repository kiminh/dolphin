/*
 * Copyright (C) 2015 Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.dolphin.bsp.core;

import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.annotations.DefaultImplementation;

/**
 * Interface for providing configurations setting user-defined (algorithmic-specific, application-specific) parameters.
 */
@DefaultImplementation(UserParametersImpl.class)
public interface UserParameters {
  Configuration getDriverConf();

  Configuration getServiceConf();

  Configuration getUserCmpTaskConf();

  Configuration getUserCtrlTaskConf();
}
