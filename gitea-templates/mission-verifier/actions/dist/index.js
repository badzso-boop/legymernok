const http = require("http");
const { spawn } = require("child_process");

// ANSI escape kódok (színek) eltávolítása a szövegből
function stripAnsi(text) {
  return text.replace(
    /[\u001b\u009b][[()#;?]*(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?[0-9A-ORZcf-nqry=><]/g,
    "",
  );
}

async function run() {
  const backendUrl = process.env.INPUT_BACKEND_URL || "http://backend:8080";
  const secret = process.env.INPUT_VERIFICATION_SECRET;
  const missionId = process.env.INPUT_MISSION_ID;

  if (!missionId || !secret) {
    console.error("[SYSTEM] Missing mission_id or verification_secret");
    process.exit(1);
  }

  console.log(
    `--- [SYSTEM] Starting verification for mission: ${missionId} ---`,
  );

  // 1. HTTP kérés a logok streameléséhez
  const logOptions = {
    method: "POST",
    headers: {
      "X-Verification-Secret": secret,
      "Content-Type": "text/plain",
      "Transfer-Encoding": "chunked",
    },
  };

  const logReq = http.request(
    `${backendUrl}/api/mission-verification/${missionId}/logs`,
    logOptions,
  );
  logReq.on("error", (err) =>
    console.error("[SYSTEM] Log stream error:", err.message),
  );

  // 2. npm test folyamat indítása (színek nélkül kényszerítve)
  const testProcess = spawn("npm", ["test"], {
    shell: true,
    env: { ...process.env, CI: "true", NO_COLOR: "true" },
  });

  testProcess.stdout.on("data", (data) => {
    const cleanData = stripAnsi(data.toString());
    logReq.write(cleanData);
    process.stdout.write(data); // A Gitea konzolban maradhat az eredeti
  });

  testProcess.stderr.on("data", (data) => {
    const cleanData = stripAnsi(data.toString());
    logReq.write(cleanData);
    process.stderr.write(data);
  });

  testProcess.on("close", async (code) => {
    logReq.end(); // Lezárjuk a log streamet

    const status = code === 0 ? "SUCCESS" : "FAILED";
    console.log(`\n--- [SYSTEM] Tests finished with status: ${status} ---`);

    // 3. CALLBACK KÜLDÉSE (Várakozással!)
    const callbackUrl = `${backendUrl}/api/mission-verification/${missionId}/callback?status=${status}`;

    const sendCallback = () => {
      return new Promise((resolve) => {
        console.log(`--- [SYSTEM] Reporting status to backend... ---`);
        const cbReq = http.request(callbackUrl, {
          method: "POST",
          headers: { "X-Verification-Secret": secret },
        });

        cbReq.on("response", (res) => {
          console.log(`[SYSTEM] Status reported. HTTP ${res.statusCode}`);
          resolve();
        });

        cbReq.on("error", (e) => {
          console.error("[SYSTEM] Callback failed:", e.message);
          resolve(); // Hibánál is továbbmegyünk, hogy lezárjuk a processzt
        });

        cbReq.end();
      });
    };

    await sendCallback();

    // Csak miután megérkezett a válasz a backendtől, csak akkor lépünk ki
    process.exit(code === 0 ? 0 : 1);
  });
}

run().catch((err) => {
  console.error("[SYSTEM] Unexpected Action error:", err);
  process.exit(1);
});
