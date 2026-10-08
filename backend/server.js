/**
 * Phone Tracker - Render Backend Server
 * Receives live location pings from Android devices and serves
 * an interactive web dashboard for real-time tracking.
 */

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const path = require('path');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;

// Security and middleware
app.use(helmet({
  contentSecurityPolicy: false // Allows loading Leaflet CDN and tiles
}));
app.use(cors());
app.use(express.json());
app.use(morgan('tiny'));

// Serve static web dashboard
app.use(express.static(path.join(__dirname, 'public')));

// In-memory data store for live tracking (can easily be connected to MongoDB/PostgreSQL or Firebase Admin)
const devicesMap = new Map();
const locationHistoryMap = new Map();
const MAX_HISTORY_POINTS = 500;

/**
 * Health check endpoint for Render service monitoring & keepalive
 */
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'online',
    timestamp: new Date().toISOString(),
    uptimeSeconds: Math.floor(process.uptime()),
    activeDevicesCount: devicesMap.size
  });
});

/**
 * POST /api/location
 * Endpoint called by Android LocationTrackingService
 */
app.post('/api/location', (req, res) => {
  const {
    userId,
    userEmail,
    deviceId,
    deviceModel,
    latitude,
    longitude,
    altitude,
    accuracy,
    speed,
    batteryLevel,
    isCharging,
    timestamp
  } = req.body;

  if (!deviceId || latitude === undefined || longitude === undefined) {
    return res.status(400).json({
      error: 'Invalid payload. Missing deviceId, latitude or longitude.'
    });
  }

  const record = {
    userId: userId || 'anonymous',
    userEmail: userEmail || 'anonymous',
    deviceId,
    deviceModel: deviceModel || 'Android Device',
    latitude: Number(latitude),
    longitude: Number(longitude),
    altitude: Number(altitude || 0),
    accuracy: Number(accuracy || 0),
    speed: Number(speed || 0),
    batteryLevel: Number(batteryLevel || 0),
    isCharging: Boolean(isCharging),
    timestamp: Number(timestamp) || Date.now(),
    receivedAt: new Date().toISOString()
  };

  // Update latest device location
  devicesMap.set(deviceId, record);

  // Append to device location history
  if (!locationHistoryMap.has(deviceId)) {
    locationHistoryMap.set(deviceId, []);
  }
  const history = locationHistoryMap.get(deviceId);
  history.push(record);
  if (history.length > MAX_HISTORY_POINTS) {
    history.shift();
  }

  console.log(`[Location Ping] Device: ${deviceId} (${record.deviceModel}) -> Lat: ${latitude}, Lon: ${longitude}, Battery: ${batteryLevel}%`);

  res.status(200).json({
    success: true,
    message: 'Location recorded successfully',
    serverTimestamp: Date.now()
  });
});

/**
 * GET /api/devices
 * Returns list of all active tracked devices with their latest location
 */
app.get('/api/devices', (req, res) => {
  const devices = Array.from(devicesMap.values());
  res.status(200).json({
    count: devices.length,
    devices
  });
});

/**
 * GET /api/devices/:deviceId/last-location
 * Returns the last known location of a specific device
 */
app.get('/api/devices/:deviceId/last-location', (req, res) => {
  const { deviceId } = req.params;
  const device = devicesMap.get(deviceId);

  if (!device) {
    return res.status(404).json({
      error: 'Device not found or has not reported location yet'
    });
  }

  res.status(200).json(device);
});

/**
 * GET /api/devices/:deviceId/history
 * Returns route coordinates history for map polyline rendering
 */
app.get('/api/devices/:deviceId/history', (req, res) => {
  const { deviceId } = req.params;
  const history = locationHistoryMap.get(deviceId) || [];
  res.status(200).json({
    deviceId,
    count: history.length,
    history
  });
});

/**
 * DELETE /api/devices/:deviceId
 * Clears location data for a specific device
 */
app.delete('/api/devices/:deviceId', (req, res) => {
  const { deviceId } = req.params;
  devicesMap.delete(deviceId);
  locationHistoryMap.delete(deviceId);
  res.status(200).json({ success: true, message: `Device ${deviceId} removed` });
});

// Start Express Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`===============================================`);
  console.log(`🚀 Phone Tracker Backend running on port ${PORT}`);
  console.log(`🌍 Live Web Dashboard: http://localhost:${PORT}`);
  console.log(`📡 Render URL Ready: Environment PORT=${PORT}`);
  console.log(`===============================================`);
});
