#!/bin/bash
TOML="gradle/libs.versions.toml"

# Add versions
sed -i '/\[versions\]/a \
mpv-lib = "0.1.14"\
arthenica-smartexceptions = "0.2.1"\
ffmpeg-kit = "1.18"\
seeker = "1.2.2"\
truetypeparser = "2.1.4"\
nanohttpd-version = "2.3.1"\
torrentserver-version = "c18f58e51b"\
media-router = "1.8.1"\
cast-play-services = "22.3.1"\
' $TOML

# Add libraries
sed -i '/\[libraries\]/a \
mpv-lib = { module = "io.github.secozzi:mpv-android-lib", version.ref = "mpv-lib" }\
arthenica-smartexceptions = { module = "com.arthenica:smart-exception-java", version.ref = "arthenica-smartexceptions" }\
ffmpeg-kit = { module = "com.github.jmir1:ffmpeg-kit", version.ref = "ffmpeg-kit" }\
seeker = { module = "io.github.2307vivek:seeker", version.ref = "seeker" }\
truetypeparser = { module = "io.github.yubyf:truetypeparser-light", version.ref = "truetypeparser" }\
nanohttpd = { module = "org.nanohttpd:nanohttpd", version.ref = "nanohttpd-version" }\
torrentserver = { module = "com.github.Diegopyl1209:torrentserver-aniyomi", version.ref = "torrentserver-version" }\
media-router = { module = "androidx.mediarouter:mediarouter", version.ref = "media-router" }\
cast-play-services = { module = "com.google.android.gms:play-services-cast-framework", version.ref = "cast-play-services" }\
' $TOML

# Add bundles
sed -i '/\[bundles\]/a \
cast = ["media-router", "cast-play-services"]\
' $TOML

