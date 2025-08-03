# Mind Guard - SafeMindWatch

## Project Overview
SafeMind Watch is an integrated ecosystem designed to protect children's mental health in digital spaces through:

- **Web Dashboard**: Parental monitoring interface
- **Mobile App**: On-the-go access to child insights
- **Browser Extension**: Real-time content monitoring
- **AI Backend**: Advanced risk detection algorithms

## System Architecture

```
VIRTUSA-JatayuS4-Virtusolvers/
├── Webapp-frontend/      # React dashboard (Port 3000)
├── Webapp-backend/       # Node.js API (Port 5000)
├── App-frontend/         # Android app (Kotlin)
├── App-backend/          # Mobile API
├── Extension-frontend/   # Chrome extension frontend
├── Extension-backend/    # Chrome extension backend
└── GenAI-Backend/        # Python GenAI service
```

### Prerequisites
- Node.js v16+
- MongoDB v4.4+
- Python 3.8+
- Android Studio (for mobile app)
- Chrome browser (for extension)

### 1. Web Application Setup
```bash
git clone https://github.com/dhivyadharshinisai/VIRTUSA-JatayuS4-Virtusolvers.git
cd VIRTUSA-JatayuS4-Virtusolvers/Webapp-frontend
npm install
cd ../Webapp-backend
npm install
```

**Environment Configuration**:
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

### 2. Mobile App Setup
1. Open `App-frontend/` in Android Studio
2. Sync Gradle dependencies
3. Configure `RetrofitClient.kt` and `network_security_config.xml` with your local IP
4. cd VIRTUSA-JatayuS4-Virtusolvers/App-backend
   npm install


**Environment Configuration**:
```bash
# App-backend/.env
MONGODB_URI=mongodb+srv://karthikroshan3456:VDdkjED7YcVPcPda@cluster0.u3lsm.mongodb.net/SMW
PORT=3000
JWT_SECRET=2d1b1c43f58573c63f871ca85dc88d67245b187c7f2ea9cdaa537ae967a7d50240a30ccdfcacdb3eecf9dbc8184cd1e63519291c3bbfeaef24a4b927e9df0f87
GOOGLE_CLIENT_ID = 808493242507-d1lvdphasrirkb1qnch0gaumg7p4g8bl.apps.googleusercontent.com
VONAGE_API_KEY=97ecc09f
VONAGE_API_SECRET=j0c4dLODu9wI51uH
EMAIL_USER=safemindwatch@gmail.com
EMAIL_PASS=vxayjytxdwthztho

```

### 3. Browser Extension
1. Navigate to `chrome://extensions`
2. Enable Developer Mode
3. Load unpacked extension from `/extension` folder

**Environment Configuration**:
```bash
#Extension-backend/.env
MONGO_URI=mongodb+srv://karthikroshan3456:VDdkjED7YcVPcPda@cluster0.u3lsm.mongodb.net/SMW?retryWrites=true&w=majority
ALERT_EMAIL=safemindwatch@gmail.com
ALERT_EMAIL_PASS=vxayjytxdwthztho
SMTP_PASS=vxayjytxdwthztho
SMTP_USER=safemindwatch@gmail.com
GOOGLE_CLIENT_ID=534622735292-b3dda0oqdo1rkm4hpdq6ill8gksqm622.apps.googleusercontent.com
GOOGLE_API_KEY=AIzaSyCgBDDap_5MKQ-kC-iQsHrEo8w34SE-MzY
VONAGE_API_SECRET=uIHHSgYfuDl64Kg9
VONAGE_API_KEY=32b377c4
VONAGE_BRAND_NAME=SmartMindWatch
```

### 4. AI Backend
```bash
cd genai-backend
pip install -r requirements.txt
python app.py
```

##  Key Features

### Web Dashboard
- Real-time activity monitoring
- Sentiment analysis visualization
- PDF report generation
- Multi-child profile management

### Mobile App
- Push notifications for alerts
- Quick access to child insights
- Secure authentication

### Browser Extension
- Real-time content scanning
- Harmful content blocking
- Activity logging

### AI Backend
- BERT-based risk classification
- Confidence scoring
- Continuous learning

## Running the System

| Component            | Command                | Access Point                   |
|----------------------|------------------------|--------------------------------|
| Web Frontend         | `npm start`            | http://localhost:3000          |
| Web Backend          | `node server.js`       | http://localhost:5000          |
| AI Service           | `python app.py`        | http://localhost:5001          |
| Mobile App Frontend  | Run in Android Studio  | Device/Emulator                |
| Mobile App Backend   | `node server.js`       | http://localhost:3000          |
| Extension Frontend   | Load in Chrome         | Browser Extension UI           |
| Extension Backend    | `node server.js`       | Runs in browser background     |

## Detailed Running Instructions

### 1. Web Application
```bash
# Start frontend
cd Webapp-frontend
npm start

# In another terminal, start backend
cd Webapp-backend
node server.js
```

### 2. Mobile Application
```bash
# Start mobile backend
cd App-backend
node server.js

# Then in Android Studio:
1. Open App-frontend folder
2. Click 'Run' button
3. Select target device/emulator
```

### 3. Browser Extension
```bash
# Start extension backend
cd Extension-backend
node server.js

# Then in Chrome:
1. Navigate to chrome://extensions
2. Enable Developer Mode
3. Click "Load unpacked"
4. Select the Extension-frontend folder
```

### 4. AI Backend
```bash
cd GenAI-Backend
python app.py
```

## Project Structure Details

### Web Components
- Frontend: React with Chart.js for visualizations
- Backend: Express.js with MongoDB/Mongoose

### Mobile Components
- Retrofit for API communication
- Material Design UI components
- Secure local storage

### AI Components
- Fine-tuned BERT model
- Flask API endpoints
- Confidence thresholding
