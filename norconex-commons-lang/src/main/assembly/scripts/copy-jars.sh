#!/bin/sh
# Copyright 2017 Norconex Inc.
# 
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
# 
#      http://www.apache.org/licenses/LICENSE-2.0
# 
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
cd $(dirname $0)

if [ "$#" -ne 2 ]; then
    echo ""
    echo "Usage: $0 source target"
    echo ""
    echo "Safe copy of one or several jar files, resolving conflicts."
    echo ""
    echo "Arguments:"
    echo "  source   jar file or directory containing jar files to copy"
    echo "  target   directory where to copy the jars to"
    exit 1
fi

java -Dfile.encoding=UTF8 -cp "./lib/*:../lib/*" com.norconex.commons.lang.jar.JarCopier "$@"

