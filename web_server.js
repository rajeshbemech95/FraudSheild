const http = require("http");
const https = require("https");
const fs = require("fs");
const path = require("path");

const PORT = 3000;
const ENV_PATH = path.join(__dirname, ".env");

// Helper to parse .env file
function getEnvValue(key, defaultValue = "") {
  try {
    if (fs.existsSync(ENV_PATH)) {
      const content = fs.readFileSync(ENV_PATH, "utf8");
      const lines = content.split("\n");
      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith("#") || !trimmed.includes("=")) continue;
        const [k, ...rest] = trimmed.split("=");
        if (k.trim() === key) {
          let val = rest.join("=").trim();
          if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
            val = val.slice(1, -1);
          }
          return val;
        }
      }
    }
  } catch (err) {
    console.error("Error reading .env:", err.message);
  }
  return process.env[key] || defaultValue;
}

// Deterministic Local Triage Rules (mirrors Android TriageEngine)
function localTriage(text, stage, hasScreenshot = false) {
  const lower = (text || "").toLowerCase();

  if (stage === "SENT_MONEY") {
    return {
      riskLevel: "CRITICAL",
      headline: "Financial Loss Imminent: Immediate Action Required",
      summary: "Money was transferred or approved. The golden hour window is active to freeze recipient bank accounts.",
      stage: stage,
      verifiedObservations: [
        "User confirmed a financial transaction occurred (SENT_MONEY stage)",
        "Potential illicit fund transfer in progress"
      ],
      aiInferences: [
        "Account freeze probability drops exponentially after the first 2-3 hours"
      ],
      unknowns: [
        "Recipient bank account / UPI ID details",
        "Transaction UTR / Reference number"
      ],
      prioritizedActions: [
        { id: "act_call_1930", title: "Call 1930 Cyber Helpline Immediately", description: "Dial 1930 to report the transaction ID and trigger beneficiary bank hold.", isUrgent: true, isCompleted: false },
        { id: "act_call_bank", title: "Contact Bank Fraud Helpline", description: "Request an immediate recall and freeze on the transaction reference.", isUrgent: true, isCompleted: false },
        { id: "act_file_cybercrime", title: "Register on cybercrime.gov.in", description: "Obtain formal acknowledgement number for police and bank tracking.", isUrgent: false, isCompleted: false }
      ],
      emergencyTriggered: true,
      analysisSource: "LOCAL_RULE"
    };
  }

  if (stage === "ENTERED_OTP" || (lower.includes("otp") && (lower.includes("share") || lower.includes("verify")))) {
    return {
      riskLevel: "HIGH",
      headline: "Potential Account Takeover / Unauthorized Access",
      summary: "Credentials or One-Time Passwords were submitted. Unauthorized sessions or debit attempts may occur.",
      stage: stage,
      verifiedObservations: [
        "User entered OTP or sensitive credentials",
        "High susceptibility to credential harvesting"
      ],
      aiInferences: [
        "Perpetrator attempting SIM swap, UPI linking, or net banking takeover"
      ],
      unknowns: [
        "Whether subsequent 2FA requests were triggered"
      ],
      prioritizedActions: [
        { id: "act_change_passwords", title: "Change Banking & Account Passwords", description: "Immediately change passwords on a secure, separate device.", isUrgent: true, isCompleted: false },
        { id: "act_block_cards", title: "Temporarily Block Cards / Net Banking", description: "Lock cards and internet transactions via your bank's official mobile app.", isUrgent: true, isCompleted: false }
      ],
      emergencyTriggered: false,
      analysisSource: "LOCAL_RULE"
    };
  }

  const isImpersonation = lower.includes("customs") || lower.includes("police") || lower.includes("cbi") || lower.includes("arrest") || lower.includes("fedex");
  const isElectricity = lower.includes("electricity") || lower.includes("power") || lower.includes("disconnected") || lower.includes("bill");
  const isJob = lower.includes("winner") || lower.includes("lottery") || lower.includes("part-time") || lower.includes("telegram task");

  let risk = "LOW";
  let headline = "Routine Communication Analysis";
  let summary = "No definitive high-threat indicators identified. Exercise standard caution before sharing personal information.";

  if (isImpersonation) {
    risk = "CRITICAL";
    headline = "Law Enforcement / Delivery Impersonation Scam Detected";
    summary = "The message mimics government, police, or courier officials using urgency and coercion to extort funds.";
  } else if (isElectricity) {
    risk = "HIGH";
    headline = "Utility Disconnection Phishing Pattern";
    summary = "Fake utility notice threatening immediate disconnection to force downloading malicious APKs or making payments.";
  } else if (isJob) {
    risk = "SUSPICIOUS";
    headline = "Unsolicited Reward / Task Employment Scam";
    summary = "Promises of unrealistic returns or task-based earnings intended to solicit initial deposit sums.";
  } else if (stage === "CLICKED_LINK") {
    risk = "SUSPICIOUS";
    headline = "Suspicious Link Tapped";
    summary = "You tapped an external link. Verify your browser history and check for unauthorized file downloads.";
  }

  return {
    riskLevel: risk,
    headline,
    summary,
    stage: stage,
    verifiedObservations: [
      "Evaluated communication using deterministic threat signature matcher",
      `Reported progress stage: ${stage}`
    ],
    aiInferences: [
      risk === "CRITICAL" || risk === "HIGH" ? "Matches recognized active threat pattern" : "Low threat indicator correlation"
    ],
    unknowns: [
      "Authenticity of sender phone number or email address"
    ],
    prioritizedActions: [
      { id: "act_do_not_pay", title: "Do Not Transfer Funds or Share OTP", description: "Legitimate agencies never demand immediate wire or UPI transfers over phone/chat.", isUrgent: risk === "CRITICAL" || risk === "HIGH", isCompleted: false },
      { id: "act_verify_official", title: "Verify via Official Channels Only", description: "Search for official contact numbers independently; do not dial numbers in the message.", isUrgent: false, isCompleted: false }
    ],
    emergencyTriggered: risk === "CRITICAL",
    analysisSource: "LOCAL_RULE"
  };
}

// Call Gemini API via HTTPS
async function analyzeWithGemini(apiKey, text, stage, imageBase64, mimeType = "image/jpeg") {
  if (stage === "SENT_MONEY") {
    return localTriage(text, stage, !!imageBase64);
  }

  const prompt = `
You are FraudShield AI, an authoritative fraud triage engine.
Analyze the provided communication or screenshot carefully.
Classify the threat and return a strict JSON object with:
- riskLevel: one of "CRITICAL", "HIGH", "SUSPICIOUS", "LOW", "SAFE"
- headline: concise factual threat summary (under 120 chars)
- summary: clear explanation of threat mechanism and safety advice
- verifiedObservations: list of strictly observable facts from the text/image
- aiInferences: list of logical deductions/hypotheses clearly marked as inferences
- unknowns: list of unverified details (e.g. sender identity, authenticity)
- prioritizedActions: list of objects with "id", "title", "description", "isUrgent" (boolean)
- emergencyTriggered: boolean (true if emergency police/bank contact is needed)

Incident Stage context: ${stage}
User input text: ${text || "None provided; analyze screenshot if present."}
`;

  const contentsParts = [{ text: prompt }];
  if (imageBase64) {
    contentsParts.push({
      inlineData: {
        mimeType: mimeType,
        data: imageBase64
      }
    });
  }

  const requestBody = JSON.stringify({
    contents: [{ parts: contentsParts }],
    generationConfig: {
      responseMimeType: "application/json"
    }
  });

  return new Promise((resolve) => {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`;
    const req = https.request(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(requestBody)
      },
      timeout: 15000
    }, (res) => {
      let data = "";
      res.on("data", chunk => data += chunk);
      res.on("end", () => {
        try {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            const parsed = JSON.parse(data);
            const textResponse = parsed.candidates?.[0]?.content?.parts?.[0]?.text;
            if (textResponse) {
              const clean = textResponse.replace(/^```json/g, "").replace(/^```/g, "").replace(/```$/g, "").trim();
              const resultObj = JSON.parse(clean);
              resultObj.analysisSource = "REMOTE_AI";
              resultObj.stage = stage;
              // Ensure critical stage enforcement
              if (stage === "SENT_MONEY") resultObj.riskLevel = "CRITICAL";
              return resolve(resultObj);
            }
          }
        } catch (e) {
          console.error("Gemini parse error:", e.message);
        }
        resolve(localTriage(text, stage, !!imageBase64));
      });
    });

    req.on("error", (err) => {
      console.error("Gemini network error:", err.message);
      resolve(localTriage(text, stage, !!imageBase64));
    });

    req.on("timeout", () => {
      req.destroy();
      resolve(localTriage(text, stage, !!imageBase64));
    });

    req.write(requestBody);
    req.end();
  });
}

// Read HTML template
const HTML_FILE = path.join(__dirname, "public", "index.html");

const server = http.createServer(async (req, res) => {
  // CORS Headers
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  const parsedUrl = new URL(req.url, `http://${req.headers.host || "localhost"}`);
  const pathname = parsedUrl.pathname;

  // Health endpoint
  if (pathname === "/api/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok", app: "FraudShield Web", version: "1.0.0" }));
    return;
  }

  const CASES_FILE = path.join(__dirname, "data", "cases.json");
  function readSavedCases() {
    try {
      if (fs.existsSync(CASES_FILE)) {
        return JSON.parse(fs.readFileSync(CASES_FILE, "utf8") || "[]");
      }
    } catch (e) {
      console.error("Error reading cases:", e);
    }
    return [];
  }

  function writeSavedCases(cases) {
    try {
      const dir = path.dirname(CASES_FILE);
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(CASES_FILE, JSON.stringify(cases, null, 2), "utf8");
    } catch (e) {
      console.error("Error writing cases:", e);
    }
  }

  // Cases API endpoints
  if (pathname === "/api/cases" && req.method === "GET") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify(readSavedCases()));
    return;
  }

  if (pathname === "/api/cases" && req.method === "POST") {
    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", () => {
      try {
        const item = JSON.parse(body || "{}");
        if (!item.timestamp) item.timestamp = Date.now();
        const cases = readSavedCases();
        const idx = cases.findIndex(c => c.timestamp === item.timestamp);
        if (idx >= 0) {
          cases[idx] = item;
        } else {
          cases.unshift(item);
        }
        writeSavedCases(cases);
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ success: true, count: cases.length }));
      } catch (e) {
        res.writeHead(500, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ error: e.message }));
      }
    });
    return;
  }

  if (pathname === "/api/cases" && req.method === "DELETE") {
    writeSavedCases([]);
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ success: true, count: 0 }));
    return;
  }

  // Triage API endpoint
  if (pathname === "/api/triage" && req.method === "POST") {
    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", async () => {
      try {
        const payload = JSON.parse(body || "{}");
        const apiKey = getEnvValue("GEMINI_API_KEY");
        const result = (apiKey && apiKey !== "DEFAULT_GEMINI_API_KEY")
          ? await analyzeWithGemini(apiKey, payload.messageText || "", payload.incidentStage || "RECEIVED", payload.imageBase64, payload.mimeType)
          : localTriage(payload.messageText || "", payload.incidentStage || "RECEIVED", !!payload.imageBase64);

        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify(result));
      } catch (err) {
        res.writeHead(500, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ error: err.message }));
      }
    });
    return;
  }

  // Serve Single-Page Application HTML
  if (pathname === "/" || pathname === "/index.html" || !pathname.includes(".")) {
    try {
      if (fs.existsSync(HTML_FILE)) {
        const content = fs.readFileSync(HTML_FILE, "utf8");
        res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
        res.end(content);
        return;
      }
    } catch (e) {
      console.error("Error reading index.html:", e);
    }
  }

  // Fallback 404
  res.writeHead(404, { "Content-Type": "text/plain" });
  res.end("Not Found");
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`FraudShield Web Application listening on port ${PORT}`);
});
