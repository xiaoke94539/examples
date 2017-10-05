#!/bin/bash

export GOOGLE_PROTOC=/opt/local/bin/protoc
if [ -z "$GOOGLE_PROTOC" ]; then
    echo "ERROR: Environment variable is not defined: GOOGLE_PROTOC";
    exit 1;
fi;

set -e;

$GOOGLE_PROTOC src/main/resources/switch/metadata-pb-api/**/*.proto --java_out=src/main/java/ --proto_path=src/main/resources/switch/metadata-pb-api/ --proto_path=src/main/resources/switch/device-pb-api/;
$GOOGLE_PROTOC `find src/main/resources/switch/device-pb-api/ -name "*.proto" | egrep 'stats|enums|pbjson|types|nanopb'` --java_out=src/main/java/ --proto_path=src/main/resources/switch/metadata-pb-api/ --proto_path=src/main/resources/switch/device-pb-api/;

exit;
