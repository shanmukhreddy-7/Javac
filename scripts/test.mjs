import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));const windows=process.platform==='win32';
for(const [command,args] of [['npm',['--prefix','frontend','test']],['maven',['test']]]){const maven=command==='maven';const result=spawnSync(maven?(windows?'mvnw.cmd':'./mvnw'):(windows?'npm.cmd':'npm'),args,{cwd:maven?root+'backend':root,stdio:'inherit',shell:windows});if(result.error){console.error(result.error.message);process.exit(1);}if(result.status!==0)process.exit(result.status??1);}
