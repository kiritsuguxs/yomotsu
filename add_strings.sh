#!/bin/bash
BASE="i18n/src/commonMain/moko-resources/base/strings.xml"
PTBR="i18n/src/commonMain/moko-resources/pt-rBR/strings.xml"

sed -i '/<string name="label_extensions">/a \    <string name="label_animeextensions">Anime Extensions</string>\n    <string name="label_animesources">Anime Sources</string>\n    <string name="label_anime">Anime</string>' $BASE

sed -i '/<string name="label_extensions">/a \    <string name="label_animeextensions">Extensões de Anime</string>\n    <string name="label_animesources">Fontes de Anime</string>\n    <string name="label_anime">Anime</string>' $PTBR
