/* Copyright 2019 Norconex Inc.
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
package com.norconex.commons.lang;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.convert.IConverter;

/**
 * Class-scanning tests
 * @author Pascal Essiembre
 */
public class ClassFinderTest {

    @Test
    public void findClassTest() throws IOException {

        String toFind = ".DurationConverter";

        List<String> types = ClassFinder.findSubTypes(
                IConverter.class, s -> s.endsWith(toFind));
        Assertions.assertTrue(types.contains(
                "com.norconex.commons.lang.convert.DurationConverter"));
        Assertions.assertEquals(1, types.size());
    }
}
