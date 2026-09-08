const http = require('http');

const TOKEN = process.env.BEARER_TOKEN || 'changeme';

const server = http.createServer((req, res) => {
  const auth = req.headers.authorization;

  if (!auth || auth !== `Bearer ${TOKEN}`) {
    res.writeHead(401, { 'Content-Type': 'text/plain' });
    res.end('Unauthorized');
    return;
  }

  // Burn CPU to simulate busy workload
  let x = 0;
  for (let i = 0; i < 1e6; i++) x += Math.sqrt(i);

  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('Busy app running - load: ' + x);
});

server.listen(process.env.PORT || 3000, () => {
  console.log('Busy app listening on port ' + (process.env.PORT || 3000));
});
