# DocPilot

> 闈㈠悜 AI 鏂囨。闂瓟鍦烘櫙鐨勫叏鏍堝伐绋嬮」鐩細瑕嗙洊璐﹀彿璁よ瘉銆佹枃浠朵笂浼犮€佸紓姝ヨВ鏋愩€佽交閲忔绱㈠寮洪棶绛旓紙鍚?SSE 娴佸紡杈撳嚭锛夈€?> 
> 椤圭洰閲嶇偣涓嶅湪鈥滃爢鍔熻兘椤碘€濓紝鑰屽湪鍙獙璇佺殑宸ョ▼閾捐矾锛歄utbox + RocketMQ 寮傛鎶曢€掍笌琛ュ伩鏈哄埗銆丷edis/Redisson 骞傜瓑涓庨檺娴併€丮inIO 鍒嗙墖涓婁紶銆丳rometheus 鎸囨爣鍙娴嬨€?
## Why This Project

DocPilot 閫傚悎浣滀负鍚庣宸ョ▼ + 鍏ㄦ爤鑱旇皟鑳藉姏鐨勫睍绀烘牱鏈細
- 涓氬姟閾捐矾鍙鍒扮婕旂ず锛氭敞鍐?鐧诲綍 -> 涓婁紶 -> 鍒涘缓瑙ｆ瀽浠诲姟 -> 鏂囨。璇︽儏 -> AI 闂瓟
- 鍏抽敭涓棿浠跺彲鍒囨崲锛氶粯璁ゆ寜鈥滄湰鍦板簲鐢?+ 棣欐腐浜戜腑闂翠欢鈥濆紑鍙戯紝涔熷彲鍒囧埌鏈湴 demo
- 闈㈠悜鐪熷疄绾︽潫锛氶檺娴併€佸箓绛夈€佸紓姝ヨˉ鍋裤€佸彲瑙傛祴鎬с€侀敊璇檷绾ч兘鍦ㄤ富閾捐矾鍐呭彲瑙?

## 鏍稿績浜偣

- **Outbox + RocketMQ 寮傛瑙ｆ瀽閾捐矾**锛歚task/parse/create` 杩斿洖鍚庡紓姝ユ帹杩涳紝鍚ˉ鍋挎壂鎻忎笌閲嶆姇銆?- **娑堣垂骞傜瓑 + 鍒嗗竷寮忛攣**锛氭秷璐硅褰曞幓閲?+ Redisson 閿侊紙WatchDog锛夋敹鏁涘苟鍙戦噸澶嶆墽琛屻€?- **MinIO + 鍒嗙墖涓婁紶/鏂偣缁紶**锛氭敮鎸佹櫘閫氫笂浼犱笌鍒嗙墖浼氳瘽锛屽惈鐘舵€佹煡璇笌鍚堝苟瀹屾垚銆?- **AI 闂瓟 + SSE 娴佸紡杈撳嚭**锛氳鎯呴〉鏀寔鏅€氶棶绛斾笌娴佸紡闂瓟鍒囨崲锛涙祦寮忓け璐ユ椂鍓嶇浼氬皾璇曡嚜鍔ㄩ檷绾у埌鏅€氶棶绛旓紝骞跺洖浼犲紩鐢ㄥ厓淇℃伅銆?- **Redis 娌荤悊鑳藉姏**锛氭枃妗?闂瓟缂撳瓨銆侀棶绛斾护鐗屾《闄愭祦銆佺煭鏈熶細璇濅笂涓嬫枃銆?- **鍙娴嬫€т笌鍘嬫祴鍩虹嚎**锛欰ctuator/Prometheus 鎸囨爣 + benchmark harness + smoke 鑴氭湰銆?
## 绯荤粺涓婚摼璺?
1. **娉ㄥ唽/鐧诲綍**锛氬墠绔?`/login` 榛樿娉ㄥ唽妯″紡锛岃璇佷富鍏ュ彛涓鸿处鍙峰瘑鐮併€?2. **涓婁紶鏂囦欢**锛氭敮鎸?`txt/md/pdf` 涓婁紶锛涘綋鍓?`txt/md` 鍙繘鍏ヨВ鏋愪富閾捐矾锛宍pdf` 瑙ｆ瀽涓哄崰浣嶈兘鍔涖€?3. **寮傛瑙ｆ瀽**锛氬墠绔疆璇㈢姸鎬侊紝鍚庣寮傛娑堣垂鎺ㄨ繘鍒扮粓鎬併€?4. **鏂囨。娴忚**锛氬垪琛ㄦ煡鐪嬬姸鎬侊紝璇︽儏鏌ョ湅鎽樿銆佹鏂囦笌璇佹嵁寮曠敤銆?5. **AI 闂瓟**锛氭櫘閫?SSE 闂瓟鍏变韩妫€绱笂涓嬫枃锛屾敮鎸佸紩鐢ㄧ墖娈典笌鍘嗗彶璁板綍銆?
## 鎶€鏈爤

- **Backend**: Java 17, Spring Boot 3, MyBatis-Plus, MySQL, Redis, RocketMQ, MinIO, Redisson, Micrometer
- **Frontend**: Next.js 14 (App Router), React, TypeScript, Tailwind CSS
- **Infra / Middleware**: Docker Compose, MySQL, Redis, RocketMQ, MinIO
- **Observability**: Spring Boot Actuator, Prometheus

## 閲忓寲璇勬祴锛堥樁娈?C锛?
- 鏁版嵁闆嗭細`docs/ai-dev/benchmarks/datasets/stagec_eval_dataset.json`
- 鎵ц鑴氭湰锛歚backend/scripts/benchmark/run-stage-c-eval.ps1`
- 缁撴灉鏂囨。锛歚docs/ai-dev/benchmarks/STAGEC_EVAL_RESULTS.md`
- 鍘熷 artifact锛歚docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json`

当前权威基准来自仓库内最新 artifact：`docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json`。

- `generatedAt`: `2026-04-18T18:58:42.2763129+00:00`
- `datasetName`: `stagec-core-qa-eval`
- `datasetVersion`: `2026-04-19-r2`
- `caseCount / streamPairs`: `20 / 8`
- `answerSuccessRate`: `90%`
- `citationHitRate`: `100%`
- `casePassRate`: `85%`
- `streamVsNonStreamConsistency`: `87.5%`
- Gate: `passed=true`

边界说明：以上是仓库内当前 artifact 记录，不是本轮重新运行结果；artifact 未记录实际运行时 `AI_MODE`、模型名或 provider；该结果用于本地版本证据链，不代表线上 SLA。后续需通过 T005 重新运行 eval，并补充运行时配置记录。
## 椤甸潰棰勮

鍏紑浠撳簱褰撳墠鏈撼鍏モ€滄寮忓睍绀烘埅鍥捐祫浜р€濄€?
濡傛灉浣犳兂琛ュ浘锛屽缓璁粺涓€鏀惧埌 `assets/screenshots/`锛屾帹鑽愰『搴忥細
1. 鐧诲綍椤碉紙娉ㄥ唽/鐧诲綍鍙屾ā寮忥級
2. 涓婁紶椤碉紙鑷姩 create/parse锛?3. 鏂囨。鍒楄〃椤碉紙鎼滅储/绛涢€?鐘舵€侊級
4. 鏂囨。璇︽儏椤碉紙闂瓟 + 寮曠敤锛?5. SSE 娴佸紡杩囩▼鎬?
> 璇存槑锛氭湰鍦扮洰褰曞彲鑳藉瓨鍦ㄦ湭璺熻釜鐨勮皟璇曟埅鍥撅紝涓嶄綔涓?README 姝ｅ紡灞曠ず绱犳潗銆?
## 蹇€熷紑濮?
### 0) 鍥㈤槦鍐呴粯璁ゅ紑鍙戠幆澧冿紙闇€浜戜腑闂翠欢鍙揪锛?
褰撳墠浠撳簱鐨勬棩甯稿紑鍙戦粯璁ゅ彛寰勬槸锛?
- 鍓嶇锛氭湰鍦拌繍琛?- 鍚庣锛氭湰鍦拌繍琛?- 涓棿浠讹細棣欐腐浜戞湇鍔″櫒 `<CLOUD_HOST>` 涓婄殑 Docker 瀹瑰櫒

涔熷氨鏄锛屼綘涓嶅繀鍏堝湪鏈満鎷夎捣 MySQL / Redis / RocketMQ / MinIO锛屼紭鍏堜娇鐢ㄤ簯涓棿浠舵ā鏉垮嵆鍙€?
### 1) 鍓嶇疆渚濊禆

- Java 17+
- Maven 3.9+
- Node.js 20+锛堝缓璁?LTS锛?- npm 10+
- 鑻ヨ璧扳€滅函鏈湴 demo鈥濇ā寮忥紝鍐嶅噯澶?Docker Desktop

### 2) 鍚姩鍚庣锛堥粯璁わ細鏈湴搴旂敤 + 棣欐腐浜戜腑闂翠欢锛?
Windows PowerShell:

```powershell
cd backend
Copy-Item .env.cloud.example .env
mvn spring-boot:run
```

鎴栧湪 IDEA 涓洿鎺ヤ娇鐢細

- `.run/DocPilot-Backend-HK-Cloud.run.xml`
- `.run/DocPilot-Backend-App-HK-Cloud.run.xml`

璇存槑锛?
1. `application-local.yml` 鐨勯粯璁ゅ厹搴曞氨鏄娓簯涓棿浠跺湴鍧€銆?2. 鍛戒护琛屽惎鍔ㄦ椂浼氳鍙?`backend/.env`锛岃繖涔熸槸浣犲綋鍓嶉粯璁ょ殑浜戜腑闂翠欢鎺ュ叆鏂瑰紡銆?3. 棣栨浜戣仈璋冨缓璁厛淇濇寔 `ROCKETMQ_ENABLED=false`锛屽緟鍩虹鍚姩绋冲畾鍚庡啀鎵撳紑鐪熷疄娑堟伅閾捐矾銆?
鍋ュ悍妫€鏌ワ細

```bash
curl http://localhost:8081/actuator/health
```

### 3) 鍚姩鍓嶇锛堥粯璁?3000锛?
Windows PowerShell:

```powershell
cd frontend
Copy-Item .env.example .env.local
npm install
npm run dev
```

macOS/Linux:

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

璁块棶锛?
- Home: `http://localhost:3000/`
- Login: `http://localhost:3000/login`
- Dashboard: `http://localhost:3000/dashboard`

> 鑻?3000 琚崰鐢紝Next.js 浼氳嚜鍔ㄥ垏鍒?3001/3002锛岃浠ョ粓绔緭鍑虹鍙ｄ负鍑嗐€?
### 4) 鍙€夛細绾湰鍦?demo 妯″紡

濡傛灉浣犲笇鏈涘湪娌℃湁浜戜腑闂翠欢鏉冮檺鐨勬儏鍐典笅瀹屾暣婕旂ず锛屽啀浣跨敤鏈湴 compose锛?
```bash
docker compose -f docker-compose.demo.yml up -d
docker compose -f docker-compose.demo.yml ps
```

鐒跺悗浣跨敤 `backend/.env.demo.example` 鍚姩鍚庣锛?
Windows PowerShell:
```powershell
cd backend
Copy-Item .env.demo.example .env
mvn spring-boot:run
```

macOS/Linux:
```bash
cd backend
cp .env.demo.example .env
mvn spring-boot:run
```

### 5) 鍙€夛細杩愯鏈€灏忛摼璺?smoke

```powershell
powershell -ExecutionPolicy Bypass -File backend/scripts/demo/smoke-main-flow.ps1 -BaseUrl http://127.0.0.1:8081
powershell -ExecutionPolicy Bypass -File backend/scripts/demo/smoke-qa-stream.ps1 -BackendBaseUrl http://127.0.0.1:8081
```

### 6) 鍙€夛細杩愯闃舵 C 璇勬祴

```powershell
powershell -ExecutionPolicy Bypass -File backend/scripts/benchmark/run-stage-c-eval.ps1 -BackendBaseUrl http://127.0.0.1:8081
```

### 7) 鍙€夛細杩愯闃舵 D 鏈€灏?Agent smoke

```powershell
powershell -ExecutionPolicy Bypass -File backend/scripts/agent/smoke-agent-min.ps1 -BackendBaseUrl http://127.0.0.1:8081 -FilePath README.md
```

## 椤圭洰缁撴瀯

```text
DocPilot/
  backend/                 # Spring Boot 鍚庣
  frontend/                # Next.js 鍓嶇
  deploy/                  # compose 渚濊禆閰嶇疆锛圡ySQL / RocketMQ / Prometheus锛?  .run/                    # IDEA 杩愯閰嶇疆锛圔ackend/Frontend Local + HK Cloud锛?  docker-compose.demo.yml  # 鏈湴婕旂ず涓棿浠剁紪鎺?```

## 宸茬煡闄愬埗

- `pdf` 瑙ｆ瀽鐩墠涓哄崰浣嶉€昏緫锛涚湡瀹炴枃鏈В鏋愪富瑕嗙洊 `txt/md`銆?- 褰撳墠闂瓟涓衡€滆交閲忔绱㈠寮衡€濊€岄潪瀹屾暣鍚戦噺 RAG锛堟棤 embedding/vector index/rerank锛夈€?- AI 榛樿 `AI_MODE=mock`锛涘垏鎹?`real` 闇€閰嶇疆 `AI_REAL_*`銆?- 閲忓寲缁撴灉鏉ヨ嚜浠撳簱鍐呭彲澶嶇幇鑴氭湰涓庡浐瀹氭牱鏈紝鐢ㄤ簬鐗堟湰闂寸浉瀵规瘮杈冿紝涓嶇瓑鍚岀嚎涓?SLA銆?- 闃舵 C 鏍锋湰涓粛鍑虹幇杩?SSE 500锛涘綋鍓嶇敱鈥滃墠绔嚜鍔ㄩ檷绾?+ runner 閲嶈瘯鈥濆厹搴曪紝涓嶄唬琛ㄦ祦寮忕ǔ瀹氭€ч棶棰樺凡瀹屽叏闂幆銆?- RocketMQ 寮傛閾捐矾渚濊禆 `ROCKETMQ_ENABLED=true` 涓庡彲鐢?NameServer銆?- 鐭俊楠岃瘉鐮佹帴鍙ｄ负鍏煎鑱旇皟鑳藉姏锛屼笉浠ｈ〃鐢熶骇鐭俊缃戝叧鎺ュ叆銆?- Prometheus 榛樿鎶?`host.docker.internal:8081`锛孡inux 闇€鏀逛负瀹夸富鏈哄彲杈惧湴鍧€銆?- 褰撳墠榛樿寮€鍙戠幆澧冧緷璧栭娓簯涓棿浠跺彲杈撅紱鑻ヤ簯绔笉鍙敤锛岃鍒囨崲鍒?`docker-compose.demo.yml` 鐨勭函鏈湴 demo 妯″紡銆?- 褰撳墠 Agent 涓哄崟 Agent 鏈€灏忓伐鍏烽棴鐜紙鍙姝ラ锛夛紝涓嶆槸澶?Agent 缂栨帓绯荤粺銆?
---

濡傛灉浣犲湪鍑嗗闈㈣瘯婕旂ず锛屽缓璁蛋杩欐潯 5 鍒嗛挓閾捐矾锛?`娉ㄥ唽/鐧诲綍 -> 涓婁紶 -> 鑷姩鍒涘缓瑙ｆ瀽浠诲姟 -> 璇︽儏椤?SSE 闂瓟 -> Agent 椤甸潰灞曠ず宸ュ叿姝ラ涓庡紩鐢╜
