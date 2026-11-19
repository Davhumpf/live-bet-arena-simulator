import {readdirSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
const directories=['shared','test','scripts','match-generator','betting-api','match-simulator','results-engine','dashboard-backend','email-worker'];
for(const directory of directories)for(const name of readdirSync(directory))if(name.endsWith('.mjs')){
  const result=spawnSync(process.execPath,['--check',`${directory}/${name}`],{stdio:'inherit'});if(result.status)process.exit(result.status);
}
const frontend=spawnSync(process.execPath,['--check','frontend/js/arena.js'],{stdio:'inherit'});process.exitCode=frontend.status;
