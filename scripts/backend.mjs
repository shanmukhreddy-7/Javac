import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const cwd=fileURLToPath(new URL('../backend/',import.meta.url));const windows=process.platform==='win32';
const child=spawn(windows?'mvnw.cmd':'./mvnw',['spring-boot:run'],{cwd,stdio:'inherit',shell:windows});child.on('error',()=>{console.error('Java 21 and the Maven wrapper are required.');process.exitCode=1;});child.on('exit',code=>{process.exitCode=code??1;});for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>child.kill(signal));
