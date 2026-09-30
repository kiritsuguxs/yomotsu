#!/bin/bash
STRINGS_FILE=$(find . -name "strings.xml" | grep "en/strings.xml" | head -n 1)
sed -i '/<\/resources>/i \    <string name="label_animeextensions">Anime Extensions</string>' $STRINGS_FILE
sed -i '/<\/resources>/i \    <string name="label_animesources">Anime Sources</string>' $STRINGS_FILE
