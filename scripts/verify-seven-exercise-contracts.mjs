import {readFileSync,existsSync} from 'node:fs'
import {validateInput} from '../supabase/functions/generate-workout-report/contracts.ts'
import {EXERCISES} from '../supabase/functions/generate-workout-report/exercise-matrix.ts'
for (const fixture of [...EXERCISES,...EXERCISES.slice(2).map(v=>v+'-event'),...EXERCISES.map(v=>v+'-partial')]) {
  const exercise=fixture.replace(/-(event|partial)$/,'')
  const path=`build/exercise-seven-contract-fixtures/${fixture}.json`
  if(!existsSync(path)) throw Error(`Run :app:testDebugUnitTest first; missing Android-generated fixture: ${path}`)
  const raw=readFileSync(path,'utf8'),input=validateInput(JSON.parse(raw))
  if(input.schemaVersion!==2||input.exerciseType!==exercise)throw Error('Wrong generated contract identity')
  if(/landmarks|userId|email|localScore|referenceSimilarity/.test(raw))throw Error('Private/local-only field in wire fixture')
  console.log(`PASS Android serializer -> strict server V2: ${fixture}`)
}
