"""Build actual JNI binaries for four Android ABIs; verify libc++ linkage, without APKs."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--work', type=Path, required=True)
    args = parser.parse_args()
    sdk, work = args.sdk.resolve(), args.work.resolve()
    work.mkdir(parents=True, exist_ok=True)
    source = work / 'LEO source'
    shutil.copytree(Path(__file__).resolve().parents[1] / 'app/src/main/cpp', source, dirs_exist_ok=True)
    suffix = '.exe' if os.name == 'nt' else ''
    host = 'windows-x86_64' if os.name == 'nt' else 'linux-x86_64'
    cmake_bin = sdk / 'cmake/3.22.1/bin'
    ndk = sdk / 'ndk/27.0.12077973'
    readelf = ndk / 'toolchains/llvm/prebuilt' / host / 'bin' / ('llvm-readelf' + suffix)
    for abi in ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'):
        build = work / ('build ' + abi)
        with (work / (abi + '.log')).open('w', encoding='utf-8') as log:
            def run(command):
                result = subprocess.run([str(v) for v in command], capture_output=True, text=True)
                output = result.stdout + result.stderr
                log.write(output)
                log.flush()
                print(output, end='', flush=True)
                result.check_returncode()
                return output
            run([cmake_bin / ('cmake' + suffix), '-S', source, '-B', build, '-G', 'Ninja',
                 '-DCMAKE_MAKE_PROGRAM=' + str(cmake_bin / ('ninja' + suffix)),
                 '-DCMAKE_TOOLCHAIN_FILE=' + str(ndk / 'build/cmake/android.toolchain.cmake'),
                 '-DANDROID_ABI=' + abi, '-DANDROID_PLATFORM=android-31',
                 '-DANDROID_STL=c++_shared', '-DCMAKE_BUILD_TYPE=Debug'])
            run([cmake_bin / ('cmake' + suffix), '--build', build, '--parallel', '2', '--verbose'])
            dynamic = run([readelf, '--dynamic', build / 'libleo_semantic_jni.so'])
            if '[libc++_shared.so]' not in dynamic:
                raise RuntimeError(abi + ': JNI library is not linked to libc++_shared.so')
            log.write(abi + ': actual native link and runtime dependency verified\n')


if __name__ == '__main__':
    main()
