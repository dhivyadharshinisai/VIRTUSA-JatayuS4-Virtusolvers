import { Routes, Route, Navigate } from 'react-router-dom';
import React, { useState, useEffect } from 'react';
import './Styles/App.css';
import Login from './Login.js';
import Signup from './Signup.js';
import HealthCareProvider from './HealthCareProvider.js';
import Dashboard from './Dashboard.js';
import Profile from './Profile.js';
import ChildProfileSelector from './ChildProfileSelector.js';
import AllSearchData from './AllSearchData.js';
import DetailedDataPage from './DetailedDataPage.js';
import SOSReports from './SOSReports.js';
import Terms from './Terms.js';
import PrivacyPolicy from './PrivacyPolicy.js';

function App() {
  const [userDetails, setUserDetails] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadUser = async () => {
      const token = localStorage.getItem('token');
      const storedUser = localStorage.getItem('user');
      
      if (storedUser) {
        try {
          const user = JSON.parse(storedUser);
          setUserDetails(user);
        } catch (e) {
          console.error('Failed to parse user:', e);
        }
      } else if (token) {
        try {
          const response = await fetch('http://localhost:5000/api/user/profile', {
            headers: { 'Authorization': `Bearer ${token}` }
          });
          const userData = await response.json();
          localStorage.setItem('user', JSON.stringify(userData));
          setUserDetails(userData);
        } catch (err) {
          console.error('Failed to fetch user:', err);
        }
      }
      setLoading(false);
    };

    loadUser();

    const handleStorageChange = () => {
      const user = localStorage.getItem('user');
      setUserDetails(user ? JSON.parse(user) : null);
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

  const handleLogout = () => {
    localStorage.removeItem("user");
    localStorage.removeItem("token");
    setUserDetails(null);
    window.location.href = '/';
  };

  if (loading) {
    return <div className="loading-screen">Loading application...</div>;
  }

  return (
    <Routes>
      <Route path="/" element={userDetails ? <Navigate to="/child-profile" replace /> : <Login setUserDetails={setUserDetails} />} />
      <Route path="/signup" element={<Signup setUserDetails={setUserDetails} />} />
      <Route path="/healthcare" element={<HealthCareProvider userDetails={userDetails} />} />
      <Route path="/sos-reports" element={<SOSReports userDetails={userDetails} />} />
      <Route path="/all-search-data" element={userDetails ? <AllSearchData userDetails={userDetails} onLogout={handleLogout} /> : <Navigate to="/" replace />} />
      <Route path="/child-profile" element={userDetails ? <ChildProfileSelector user={userDetails} /> : <Navigate to="/" replace />} />
      <Route path="/dashboard" element={<Dashboard userDetails={userDetails} onLogout={handleLogout} />} />
      <Route path="/profile" element={<Profile userDetails={userDetails} onLogout={handleLogout} />} />
      <Route path="/add-child" element={<div>Add Child Form Here</div>} />
      <Route path="/detailed-data" element={<DetailedDataPage />} />
      <Route path="/terms" element={<Terms />} />
      <Route path="/privacy" element={<PrivacyPolicy />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;