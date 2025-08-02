import React, { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import logo from './assets/logoimg.jpeg';
import './Styles/SOSReports.css';

const SOSReports = () => {
  const location = useLocation();
  const { parentUser, selectedChild } = location.state || {};
  const [sosReports, setSosReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('all');
  const [specificDate, setSpecificDate] = useState('');
  const [customRange, setCustomRange] = useState({ start: '', end: '' });
  const navigate = useNavigate();

  const getCurrentDate = () => {
    const today = new Date();
    return today.toISOString().split('T')[0];
  };

  const getLastWeekRange = () => {
    const end = new Date();
    const start = new Date();
    start.setDate(end.getDate() - 7);
    return {
      start: start.toISOString().split('T')[0],
      end: end.toISOString().split('T')[0]
    };
  };

  useEffect(() => {
    const fetchSOSReports = async () => {
      try {
        if (!parentUser?._id || !selectedChild?.name) return;

        let url = `http://localhost:5000/api/sos-reports?userId=${parentUser._id}&childName=${encodeURIComponent(selectedChild.name)}`;
        
        // Add filter parameters based on selection
        if (filter === 'today') {
          url += `&date=${getCurrentDate()}`;
        } else if (filter === 'last_week') {
          const range = getLastWeekRange();
          url += `&startDate=${range.start}&endDate=${range.end}`;
        } else if (filter === 'specific_date' && specificDate) {
          url += `&date=${specificDate}`;
        } else if (filter === 'custom_range' && customRange.start && customRange.end) {
          url += `&startDate=${customRange.start}&endDate=${customRange.end}`;
        }

        setLoading(true);
        const response = await fetch(url);
        const result = await response.json();

        if (result.success) {
          setSosReports(result.data || []);
        } else {
          setSosReports([]);
        }
      } catch (error) {
        console.error('Error fetching reports:', error);
        setSosReports([]);
      } finally {
        setLoading(false);
      }
    };

    fetchSOSReports();
  }, [parentUser, selectedChild, filter, specificDate, customRange]);

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  const handleFilterChange = (e) => {
    setFilter(e.target.value);
    if (e.target.value !== 'specific_date') setSpecificDate('');
    if (e.target.value !== 'custom_range') setCustomRange({ start: '', end: '' });
  };

  const handleDashboardClick = () => {
    navigate('/dashboard', { state: { parentUser } });
  };

  return (
    <div className="sos-reports-page">
      <div className="sos-reports-nav">
        <div className="sos-reports-logo-container">
          <img src={logo} alt="logo" className="sos-reports-logo" />
          <h2>SafeMind Watch</h2>
        </div>
        <div className="sos-reports-profile-nav" onClick={() => navigate('/profile')}>
          Welcome, {parentUser?.name || 'User'}
        </div>
      </div>

      <aside className="sos-reports-sidebar">
        <nav className="sos-reports-nav-links">
          <ul>
            <li className="sos-reports-nav-item" onClick={handleDashboardClick}>
              <i className="fas fa-chart-pie sos-reports-nav-icon"></i> Dashboard
            </li>
            <li className="sos-reports-nav-item active">
              <i className="fas fa-exclamation-triangle sos-reports-nav-icon"></i> SOS Reports
            </li>
          </ul>
        </nav>
      </aside>

      <main className="sos-reports-main">
        <div className="sos-reports-header">
          <h2 className="sos-reports-title">{selectedChild?.name}'s SOS Reports</h2>
          <p className="sos-reports-subtitle">Critical alerts that required immediate attention</p>
          
          <div className="sos-reports-filter-bar">
            <label className="sos-reports-filter-label">Filter by:</label>
            <select 
              value={filter} 
              onChange={handleFilterChange}  
              className="sos-reports-filter-select"
            >
              <option value="all">All Reports</option>
              <option value="today">Today</option>
              <option value="last_week">Last 7 Days</option>
              <option value="specific_date">Specific Date</option>
              <option value="custom_range">Custom Range</option>
            </select>
            
            {filter === 'specific_date' && (
              <input
                type="date"
                value={specificDate}
                onChange={(e) => setSpecificDate(e.target.value)}
                max={getCurrentDate()}
                className="sos-reports-date-input"
              />
            )}

            {filter === 'custom_range' && (
              <div className="sos-reports-date-range-picker">
                <input
                  type="date"
                  value={customRange.start}
                  onChange={(e) => setCustomRange({...customRange, start: e.target.value})}
                  max={customRange.end || getCurrentDate()}
                  className="sos-reports-date-input"
                />
                <span className="sos-reports-date-range-separator">to</span>
                <input
                  type="date"
                  value={customRange.end}
                  onChange={(e) => setCustomRange({...customRange, end: e.target.value})}
                  min={customRange.start}
                  max={getCurrentDate()}
                  className="sos-reports-date-input"
                />
              </div>
            )}
          </div>
        </div>

        {loading ? (
          <div className="sos-reports-loading">
            <i className="fas fa-circle-notch fa-spin sos-reports-loading-icon"></i>
            <p>Loading reports...</p>
          </div>
        ) : sosReports.length === 0 ? (
          <div className="sos-reports-empty">
            <i className="fas fa-check-circle sos-reports-empty-icon"></i>
            <h3>No SOS Reports Found</h3>
            <p>No alerts match your current filter criteria</p>
          </div>
        ) : (
          <div className="sos-reports-list">
            {sosReports.map((report) => (
              <div key={report._id} className="sos-report-card">
                <div className="sos-report-header">
                  <div className="sos-report-alert-icon">
                    <i className="fas fa-exclamation-triangle"></i>
                  </div>
                  <div className="sos-report-title-container">
                    <h3 className="sos-report-card-title">SOS Alert</h3>
                    <span className="sos-report-time">{formatDate(report.alertTime)}</span>
                  </div>
                </div>
                <div className="sos-report-content">
                  <div className="sos-report-detail">
                    <strong className="sos-report-detail-label">Triggered Query:</strong>
                    <p className="sos-report-detail-value">"{report.query}"</p>
                  </div>
                  <div className="sos-report-detail">
                    <strong className="sos-report-detail-label">Alert Time:</strong>
                    <p className="sos-report-detail-value">{formatDate(report.alertTime)}</p>
                  </div>
                  {report.location && (
                    <div className="sos-report-detail">
                      <strong className="sos-report-detail-label">Location:</strong>
                      <p className="sos-report-detail-value">{report.location}</p>
                    </div>
                  )}
                </div>
                <div className="sos-card-footer">
                  <span className="sos-status">
                    <i className="fas fa-shield-alt"></i> Alert Processed
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
};

export default SOSReports;