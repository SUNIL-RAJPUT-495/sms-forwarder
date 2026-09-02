/**
 * Manager for handling Server-Sent Events (SSE) connections for real-time Office Admin Panel
 */
let sseClients = [];

export const addClient = (req, res) => {
  // Set SSE Headers with Proxy Unbuffering for Nginx / Cloudflare
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache, no-transform');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('X-Accel-Buffering', 'no'); // Disables Nginx response buffering
  res.setHeader('Content-Encoding', 'none');
  res.flushHeaders();

  const clientId = Date.now();
  const newClient = { id: clientId, res };
  sseClients.push(newClient);

  // Send initial connection event
  res.write(`event: connected\ndata: ${JSON.stringify({ message: "Connected to SMS Admin Stream", clientId })}\n\n`);

  // Heartbeat keep-alive comment every 15s to prevent reverse proxy / Nginx timeout
  const pingInterval = setInterval(() => {
    try {
      res.write(': keep-alive\n\n');
    } catch (e) {
      clearInterval(pingInterval);
    }
  }, 15000);

  req.on('close', () => {
    clearInterval(pingInterval);
    sseClients = sseClients.filter(c => c.id !== clientId);
  });
};

export const broadcastSSE = (event, data) => {
  const payload = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
  sseClients.forEach(client => {
    try {
      client.res.write(payload);
    } catch (e) {}
  });
};

export default {
  addClient,
  broadcastSSE
};
