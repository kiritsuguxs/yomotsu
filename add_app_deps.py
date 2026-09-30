import re

with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

# find dependencies block
deps_addition = """
    // mpv-android
    implementation(libs.mpv.lib)
    implementation(libs.arthenica.smartexceptions)
    implementation(libs.ffmpeg.kit)
    implementation(libs.seeker)
    implementation(libs.truetypeparser)
    implementation(libs.nanohttpd)
    implementation(libs.torrentserver)
    implementation(libs.bundles.cast)
"""
# insert before 'testImplementation' inside dependencies
content = content.replace('    testImplementation(libs.bundles.test)', deps_addition + '    testImplementation(libs.bundles.test)')

# Add abiFilters for mpv if needed
if 'abiFilters' in content:
    pass
else:
    # Need to add "libmpv" to arguments? 
    pass

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
