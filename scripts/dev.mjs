import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));const windows=process.platform==='win32';let stopped=false;
const children=[];function stop(){if(stopped)return;stopped=true;for(const child of children)child.kill('SIGTERM');}for(const signal of ['SIGINT','SIGTERM'])process.on(signal,stop);
function run(command,args,cwd){const child=spawn(command,args,{cwd,stdio:'inherit',shell:windows});children.push(child);child.on('error',e=>{console.error(e.message);stop();process.exitCode=1;});child.on('exit',code=>{if(!stopped){stop();process.exitCode=code??1;}});return child;}
run(windows?'mvnw.cmd':'./mvnw',['spring-boot:run'],root+'backend');
console.log('Starting Java backend on http://localhost:8080 and React on http://localhost:5173');
run(windows?'npm.cmd':'npm',['run','dev'],root+'frontend');
