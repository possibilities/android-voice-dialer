"""Reuse the available official SDK/JDK. No phone access or credentials."""
import os, pathlib, subprocess, sys, urllib.parse
root=pathlib.Path(__file__).resolve().parents[1]
tool=pathlib.Path(os.environ.get('ANDROID_BUILD_TOOLS_ROOT','/workspace/shared/noizey-build'))
env=os.environ.copy()
env.update(JAVA_HOME=str(tool/'tools/jdk17'),ANDROID_HOME=str(tool/'tools/android-sdk'),GRADLE_USER_HOME=str(tool/'gradle-cache'),ANDROID_USER_HOME=str(tool/'home/.android'))
opts=['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts',f'-Duser.home={tool}/home']
p=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
if p.hostname:
 for scheme in ['http','https']: opts += [f'-D{scheme}.proxyHost={p.hostname}',f'-D{scheme}.proxyPort={p.port}']
env['GRADLE_OPTS']=' '.join(opts)
cmd=[str(tool/'tools/gradle-8.13/bin/gradle'),'--no-daemon']+(sys.argv[1:] or ['testDebugUnitTest','lintDebug','assembleDebug'])
raise SystemExit(subprocess.call(cmd,cwd=root,env=env))
