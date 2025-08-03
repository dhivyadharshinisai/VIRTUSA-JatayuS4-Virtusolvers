MindGaurd- WebApp

Project Setup

Requirements
- Node.js (v16+)
- MongoDB (v4.4+)
- npm (v8+)

Quick Start
1. Clone repository:
```bash
git clone https://github.com/dhivyadharshinisai/VIRTUSA-JatayuS4-Virtusolvers.git
cd VIRTUSA-JatayuS4-Virtusolvers
```

2. Install dependencies:
```bash
cd Webapp-frontend && npm install
cd ../Webapp-backend && npm install
```

3. Configure environment:
```bash
# Webapp-backend/.env
MONGODB_URI=mongodb+srv://karthikroshan3456:VDdkjED7YcVPcPda@cluster0.u3lsm.mongodb.net/SMW?retryWrites=true&w=majority
JWT_SECRET=c5dca2f0ac1df1a285ff107bb63d27a9ccf6113a134984c8720257455964042de97f30595c5e1643e702498e085523567126b6ca3539b29f0e37b55cb6ddb816420096c9d36ebdf1768b838fb8e92a5e9c399d62a39d55bddd27275c41a9ab7b74eb26705fd319d384f50f4941975951949cd1ac2710584742d18cb17f204f32e7a2d3bfc1351ced06bd834385ef88649d3b2c8c42f45ec12f8d83d6e920a584f4d34dce70181923f50e15813f9f577d9de8c5dd933f74c6d9fd832f32925f109c6fc85de40e0033a1c1543339cfb5a95cfdcaf0d1da6db299f0b8b58a1660941372629e32aeb6bf2723187d9583a8270b1f202f7cff5bebc2fc6bfe1b177b53
GOOGLE_CLIENT_ID=350698699552-pbf8rlh3fvds3o8lo8q6kre7j1efv5rh.apps.googleusercontent.com
REACT_APP_GOOGLE_CLIENT_ID=350698699552-pbf8rlh3fvds3o8lo8q6kre7j1efv5rh.apps.googleusercontent.com
EMAIL_USER=safemindwatch@gmail.com
EMAIL_PASSWORD=vxayjytxdwthztho
VONAGE_API_KEY=32b377c4
VONAGE_API_SECRET=uIHHSgYfuDl64Kg9
VONAGE_BRAND_NAME=SmartMindWatch
GOOGLE_API_KEY=AIzaSyDZPtvWR7c4oQB9lyKNDzn93fCjcI1hx8I

```

4. Run the system:
```bash
# Terminal 1 (backend)
cd Webapp-backend && node server.js

# Terminal 2 (frontend)
cd ../Webapp-frontend && npm start
```

## System Architecture
```
Webapp-frontend/ - React dashboard (port 3000)
  ├── src/
  │   ├── components/ - UI components
  │   └── pages/ - Application views

Webapp-backend/ - Node.js API (port 5000)
  ├── models/ - MongoDB schemas
  └── routes/ - API endpoints
```

## Key Features
- Real-time search monitoring
- Mental health risk detection
- Interactive analytics dashboard
- Secure user authentication
- PDF report generation

## Development Commands
| Command | Description |
|---------|-------------|
| `npm start` | Start frontend dev server |
| `node server.js` | Start backend API |

## Access Points
- Dashboard: http://localhost:3000

Note: Ensure MongoDB service is running before starting the backend.
