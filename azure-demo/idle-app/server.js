const http = require('http');

const TOKEN = process.env.BEARER_TOKEN || 'changeme';

const server = http.createServer((req, res) => {
  const auth = req.headers.authorization;

  if (!auth || auth !== `Bearer ${TOKEN}`) {
    res.writeHead(401, { 'Content-Type': 'text/plain' });
    res.end('Unauthorized');
    return;
  }

  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('Idle app running');
});

server.listen(process.env.PORT || 3000, () => {
  console.log('Idle app listening on port ' + (process.env.PORT || 3000));
});
