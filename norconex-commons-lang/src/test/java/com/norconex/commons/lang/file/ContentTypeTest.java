/* Copyright 2010-2019 Norconex Inc.
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
package com.norconex.commons.lang.file;

import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ContentTypeTest {

    @Test
    public void testGetDisplayName() {
        Assertions.assertEquals("Adobe Portable Document Format",
                ContentType.PDF.getDisplayName(Locale.FRENCH));
        Assertions.assertEquals("Adobe Portable Document Format",
                ContentType.PDF.getDisplayName());
        Assertions.assertEquals("Open eBook Publication Structure",
                ContentType.valueOf(
                        "application/oebps-package+xml").getDisplayName());
    }
    @Test
    public void testGetExtension() {
        Assertions.assertEquals("pdf", ContentType.PDF.getExtension());
        Assertions.assertEquals("wpd",
                ContentType.valueOf("application/wordperfect").getExtension());
        Assertions.assertArrayEquals(new String[]{ "wpd", "wp", "wp5", "wp6" },
                ContentType.valueOf("application/wordperfect").getExtensions());
    }
}
