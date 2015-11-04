/*
 * Copyright 2015 OpenCB
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

package org.opencb.opencga.storage.core.config;

import java.util.List;

/**
 * Created by imedina on 08/10/15.
 */
public class BenchmarkConfiguration {

    private List<String> storageEngines;

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BenchmarkConfiguration{");
        sb.append(", storageEngines=").append(storageEngines);
        sb.append('}');
        return sb.toString();
    }


    public List<String> getStorageEngines() {
        return storageEngines;
    }

    public void setStorageEngines(List<String> storageEngines) {
        this.storageEngines = storageEngines;
    }
}