import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import './Styles/Profile.css';
import { FaUser, FaEnvelope, FaPhone, FaBell, FaBellSlash, FaExclamationTriangle, FaCog, FaSignOutAlt } from 'react-icons/fa';

const Profile = ({ userDetails = {}, onLogout }) => {
  const DEFAULT_SETTINGS = {
    smsAlerts: true,
    emailAlerts: true,
    sosAlerts: false
  };

  const [user, setUser] = useState({
    name: '',
    email: '',
    phone: '',
    settings: DEFAULT_SETTINGS,
    ...userDetails
  });

  const [settings, setSettings] = useState(user.settings || DEFAULT_SETTINGS);
  const [isLoading, setIsLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchUserData = async () => {
      try {
        const response = await fetch(`http://localhost:5000/api/users/${user._id}`, {
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`
          }
        });

        if (response.ok) {
          const data = await response.json();
          const phoneNumber = data.phone || (data.user && data.user.phone);
          
          setUser(prev => ({
            ...prev,
            name: data.name || data.user?.name || '',
            email: data.email || data.user?.email || '',
            phone: phoneNumber || '',
            settings: data.settings || data.user?.settings || DEFAULT_SETTINGS
          }));
          
          setSettings(data.settings || data.user?.settings || DEFAULT_SETTINGS);
        }
      } catch (error) {
        alert('Failed to fetch user data');
      } finally {
        setIsLoading(false);
      }
    };

    fetchUserData();
  }, [user._id]);

  const formatPhoneNumber = (phone) => {
    if (!phone) return 'Not provided';
    
    const cleaned = phone.toString().replace(/\D/g, '');
    
    if (cleaned.startsWith('91') && cleaned.length === 12) {
      return `+${cleaned.substring(0, 2)} ${cleaned.substring(2, 7)} ${cleaned.substring(7)}`;
    }
    
    if (cleaned.length > 10) {
      return `+${cleaned}`;
    }
    
    return cleaned.replace(/(\d{3})(\d{3})(\d{4})/, '($1) $2-$3');
  };

  const handleSave = async () => {
    try {
      const response = await fetch(`http://localhost:5000/api/users/${user._id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        },
        body: JSON.stringify({ settings })
      });

      if (response.ok) {
        alert('Settings saved successfully');
      }
    } catch (error) {
      alert('Failed to save settings');
    }
  };

  const handleLogout = () => {
    localStorage.clear();
    sessionStorage.clear();
    
    if (onLogout) {
      onLogout();
    } else {
      navigate('/login');
    }
    
    fetch('http://localhost:5000/api/auth/logout', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    }).catch(() => {});
  };

  if (isLoading) {
    return (
      <div id="profile-loading-container" className="profile__loading">
        <div className="profile__loading-spinner"></div>
        <p>Loading your profile...</p>
      </div>
    );
  }

  return (
    <div id="profile-page" className="profile">
      <div className="profile__header">
        <h1><FaUser className="profile__header-icon" /> Account Settings</h1>
        <p className="profile__subheader">Manage your personal information and notification preferences</p>
      </div>

      <div className="profile__card">
        <div className="profile__section">
          <h2 className="profile__section-title">
            <FaUser className="profile__section-icon" />
            Personal Information
          </h2>
          
          <div className="profile__info-grid">
            <div className="profile__info-item">
              <label className="profile__info-label">Full Name</label>
              <div className="profile__info-value-container">
                <span className="profile__info-value">{user.name || 'Not provided'}</span>
                <FaUser className="profile__info-icon" />
              </div>
            </div>

            <div className="profile__info-item">
              <label className="profile__info-label">Email Address</label>
              <div className="profile__info-value-container">
                <span className="profile__info-value">{user.email || 'Not provided'}</span>
                <FaEnvelope className="profile__info-icon" />
              </div>
            </div>

            <div className="profile__info-item">
              <label className="profile__info-label">Phone Number</label>
              <div className="profile__info-value-container">
                <span className="profile__info-value">{formatPhoneNumber(user.phone)}</span>
                <FaPhone className="profile__info-icon" />
              </div>
            </div>
          </div>
        </div>

        <div className="profile__section">
          <h2 className="profile__section-title">
            <FaCog className="profile__section-icon" />
            Notification Preferences
          </h2>
          
          <div className="profile__settings-grid">
            <div className={`profile__setting-item ${settings.smsAlerts ? 'profile__setting-item--active' : ''}`}>
              <div className="profile__setting-content">
                <h3 className="profile__setting-label">SMS Notifications</h3>
                <p className="profile__setting-description">Receive important alerts via text message</p>
              </div>
              <div className="profile__setting-toggle">
                {settings.smsAlerts ? (
                  <FaBell className="profile__setting-icon profile__setting-icon--active" />
                ) : (
                  <FaBellSlash className="profile__setting-icon" />
                )}
                <label className="profile__switch">
                  <input 
                    type="checkbox" 
                    checked={settings.smsAlerts}
                    onChange={() => setSettings(s => ({ ...s, smsAlerts: !s.smsAlerts }))}
                  />
                  <span className="profile__slider profile__slider--round"></span>
                </label>
              </div>
            </div>

            <div className={`profile__setting-item ${settings.emailAlerts ? 'profile__setting-item--active' : ''}`}>
              <div className="profile__setting-content">
                <h3 className="profile__setting-label">Email Notifications</h3>
                <p className="profile__setting-description">Get updates and reports via email</p>
              </div>
              <div className="profile__setting-toggle">
                {settings.emailAlerts ? (
                  <FaBell className="profile__setting-icon profile__setting-icon--active" />
                ) : (
                  <FaBellSlash className="profile__setting-icon" />
                )}
                <label className="profile__switch">
                  <input 
                    type="checkbox" 
                    checked={settings.emailAlerts}
                    onChange={() => setSettings(s => ({ ...s, emailAlerts: !s.emailAlerts }))}
                  />
                  <span className="profile__slider profile__slider--round"></span>
                </label>
              </div>
            </div>

            <div className={`profile__setting-item ${settings.sosAlerts ? 'profile__setting-item--active' : ''}`}>
              <div className="profile__setting-content">
                <h3 className="profile__setting-label">Emergency Alerts</h3>
                <p className="profile__setting-description">Critical notifications that require immediate attention</p>
              </div>
              <div className="profile__setting-toggle">
                <FaExclamationTriangle className={`profile__setting-icon ${settings.sosAlerts ? 'profile__setting-icon--active' : ''}`} />
                <label className="profile__switch">
                  <input 
                    type="checkbox" 
                    checked={settings.sosAlerts}
                    onChange={() => setSettings(s => ({ ...s, sosAlerts: !s.sosAlerts }))}
                  />
                  <span className="profile__slider profile__slider--round"></span>
                </label>
              </div>
            </div>
          </div>
        </div>

        <div className="profile__action-buttons">
          <button className="profile__save-btn" onClick={handleSave}>
            Save Changes
          </button>
          <button 
            className="profile__logout-btn"
            onClick={handleLogout}
          >
            <FaSignOutAlt /> Logout
          </button>
        </div>
      </div>
    </div>
  );
};

export default Profile;