const targetWs = process.argv[2];
const expressionArg = process.argv[3];

if (!targetWs || !expressionArg) {
  console.error("usage: node devtools_eval.js <ws-url> <expression|@file>");
  process.exit(1);
}

const fs = require("fs");
const expression = expressionArg.startsWith("@")
  ? fs.readFileSync(expressionArg.slice(1), "utf8")
  : expressionArg;

const ws = new WebSocket(targetWs);
let done = false;

ws.addEventListener("open", () => {
  ws.send(JSON.stringify({ id: 1, method: "Runtime.enable" }));
  ws.send(
    JSON.stringify({
      id: 2,
      method: "Runtime.evaluate",
      params: {
        expression,
        returnByValue: true,
      },
    }),
  );
});

ws.addEventListener("message", (event) => {
  const text = String(event.data);
  console.log(text);
  if (text.includes('"id":2')) {
    done = true;
    ws.close();
  }
});

ws.addEventListener("error", (event) => {
  console.error(event);
  process.exit(1);
});

setTimeout(() => {
  if (!done) {
    console.error("timeout");
    process.exit(2);
  }
}, 5000);
