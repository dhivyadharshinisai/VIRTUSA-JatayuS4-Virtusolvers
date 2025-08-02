import React, { useEffect, useState, useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import PropTypes from 'prop-types';
import logo from './assets/logoimg.jpeg';
import './Styles/DetailedDataPage.css';

const DetailedDataPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { 
    searchData: initialSearchData, 
    title, 
    currentFilter, 
    filterParams 
  } = location.state || {};
  
  const [loading, setLoading] = useState(true);
  const [filteredData, setFilteredData] = useState([]);
  const [dateRangeDescription, setDateRangeDescription] = useState('');

  const parseCustomDate = useCallback((dateInput) => {
    if (!dateInput) return null;
    
    try {
      const dateString = String(dateInput);
      
      if (dateString.includes('T')) {
        return new Date(dateString);
      }
      
      if (dateString.includes('-') && dateString.includes(',')) {
        const [datePart, timePart] = dateString.split(', ');
        const [year, month, day] = datePart.split('-').map(Number);
        const [time, period] = timePart.split(' ');
        const [hours, minutes, seconds] = time.split(':').map(Number);
        
        let hour24 = hours;
        if (period === 'pm' && hours < 12) hour24 += 12;
        if (period === 'am' && hours === 12) hour24 = 0;
        
        return new Date(year, month - 1, day, hour24, minutes, seconds);
      }
      
      return new Date(dateString);
    } catch (e) {
      console.error('Error parsing date:', dateInput, e);
      return null;
    }
  }, []);

  const formatTimeSpent = useCallback((seconds) => {
    if (seconds == null) return 'N/A';
    const secs = parseInt(seconds);
    if (isNaN(secs)) return 'N/A';
    if (secs < 60) return `${secs} sec`;
    const minutes = Math.floor(secs / 60);
    const remainingSecs = secs % 60;
    return `${minutes} min${remainingSecs ? ` ${remainingSecs} sec` : ''}`;
  }, []);

  const formatIndianDate = useCallback((date) => {
    const d = date ? parseCustomDate(date) : null;
    return d ? d.toLocaleDateString('en-IN') : 'N/A';
  }, [parseCustomDate]);

  const formatIndianTime = useCallback((date) => {
    const d = date ? parseCustomDate(date) : null;
    return d ? d.toLocaleTimeString('en-IN', { 
      hour: '2-digit', 
      minute: '2-digit',
      hour12: true 
    }) : 'N/A';
  }, [parseCustomDate]);

  const getDateRangeDescription = useCallback((filter, params) => {
    const now = new Date();
    
    switch(filter) {
      case 'today':
        return `Showing data for today (${formatIndianDate(now)})`;
      
      case 'this_week':
        const thisWeekStart = new Date(now);
        thisWeekStart.setDate(now.getDate() - now.getDay());
        const thisWeekEnd = new Date(now);
        thisWeekEnd.setDate(now.getDate() + (6 - now.getDay()));
        return `Showing this week's data (${formatIndianDate(thisWeekStart)} to ${formatIndianDate(thisWeekEnd)})`;
      
      case 'last_week':
        const lastWeekStart = new Date(now);
        lastWeekStart.setDate(now.getDate() - 7 - now.getDay());
        const lastWeekEnd = new Date(now);
        lastWeekEnd.setDate(now.getDate() - 7 + (6 - now.getDay()));
        return `Showing last week's data (${formatIndianDate(lastWeekStart)} to ${formatIndianDate(lastWeekEnd)})`;
      
      case 'specific_date':
        return params?.specificDate 
          ? `Showing data for ${formatIndianDate(params.specificDate)}` 
          : 'Showing all data';
      
      case 'custom_range':
        return params?.customRange?.start && params?.customRange?.end
          ? `Showing data from ${formatIndianDate(params.customRange.start)} to ${formatIndianDate(params.customRange.end)}`
          : 'Showing all data';
      
      default:
        return 'Showing all available data';
    }
  }, [formatIndianDate]);

  const getChildAndCategory = useCallback((title) => {
    if (!title) return { childName: "User", category: "Data" };
    
    if (title.includes("'s ")) {
      const [child, rest] = title.split("'s ");
      const category = rest.replace(" Searches", "").trim();
      return { childName: child.trim(), category };
    }
    
    return { 
      childName: "User", 
      category: title.replace(" Searches", "").trim() 
    };
  }, []);

  const applyFilters = useCallback(() => {
    if (!initialSearchData) return;

    let result = [...initialSearchData];
    const now = new Date();
    const filter = currentFilter || 'last_week';
    const params = filterParams || {};

    if (filter === 'today') {
      const start = new Date(now);
      start.setHours(0, 0, 0, 0);
      const end = new Date(now);
      end.setHours(23, 59, 59, 999);
      
      result = result.filter(h => {
        const date = parseCustomDate(h.dateAndTime || h.timestamp);
        return date && date >= start && date <= end;
      });
    }
    else if (filter === 'this_week' || filter === 'last_week') {
      const end = new Date();
      const start = new Date();
      
      if (filter === 'last_week') {
        start.setDate(now.getDate() - 7);
        end.setDate(now.getDate() - 1);
      } else {
        start.setDate(now.getDate() - now.getDay());
        end.setDate(now.getDate() + (6 - now.getDay()));
      }

      start.setHours(0, 0, 0, 0);
      end.setHours(23, 59, 59, 999);

      result = result.filter(h => {
        const date = parseCustomDate(h.dateAndTime || h.timestamp);
        return date && date >= start && date <= end;
      });
    } 
    else if (filter === 'specific_date' && params.specificDate) {
      const [year, month, day] = params.specificDate.split('-').map(Number);
      const startDate = new Date(year, month - 1, day, 0, 0, 0);
      const endDate = new Date(year, month - 1, day, 23, 59, 59, 999);

      result = result.filter(h => {
        const date = parseCustomDate(h.dateAndTime || h.timestamp);
        return date && date >= startDate && date <= endDate;
      });
    } 
    else if (filter === 'custom_range' && params.customRange?.start && params.customRange?.end) {
      const startDate = new Date(params.customRange.start);
      const endDate = new Date(params.customRange.end);
      startDate.setHours(0, 0, 0, 0);
      endDate.setHours(23, 59, 59, 999);

      result = result.filter(h => {
        const date = parseCustomDate(h.dateAndTime || h.timestamp);
        return date && date >= startDate && date <= endDate;
      });
    }

    setFilteredData(result);
    setDateRangeDescription(getDateRangeDescription(filter, params));
  }, [initialSearchData, currentFilter, filterParams, parseCustomDate, getDateRangeDescription]);

  useEffect(() => {
    if (!initialSearchData) {
      navigate('/dashboard');
    } else {
      applyFilters();
      setLoading(false);
    }
  }, [initialSearchData, navigate, applyFilters]);

  const handleBackClick = () => {
    navigate(-1);
  };

  if (loading) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <p>Loading search data...</p>
      </div>
    );
  }

  if (!initialSearchData) {
    return (
      <div className="error-container">
        <p>No search data available</p>
        <button onClick={handleBackClick} className="back-button">
          Go Back
        </button>
      </div>
    );
  }

  const { childName, category } = getChildAndCategory(title);

  return (
    <div className="detailed-data-container">
      <div className="ddp-navbar">
        <div className="ddp-logo-title">
          <img src={logo} alt="SafeMind Watch Logo" className="ddp-logo" />
          <h2>SafeMind Watch</h2>
        </div>
        <div className="ddp-profile-nav">
          <span className="ddp-nav-profile">Detailed Search Analysis</span>
          <button onClick={handleBackClick} className="back-button">
            &larr; Back
          </button>
        </div>
      </div>

      <main className="ddp-main">
        <div className="ddp-content-wrapper">
          <div className="ddp-header">
            <div className="ddp-header-content">
              <div className="ddp-header-text">
                <h1 className="ddp-child-name">{childName}'s</h1>
                <h2 className="ddp-category-title">Search Analysis: {category}</h2>
                <p className="ddp-date-range-description">{dateRangeDescription}</p>
              </div>
              <div className="ddp-header-stats">
                <div className="ddp-stat-item">
                  <span className="ddp-stat-label">Total Records</span>
                  <span className="ddp-stat-value">{filteredData.length}</span>
                </div>
              </div>
            </div>
          </div>

          <div className="ddp-search-container">
            <div className="ddp-search-header">
              <h3 className="ddp-search-title">Search History ({filteredData.length} records)</h3>
            </div>
            {filteredData.length > 0 ? (
              <div className="ddp-table-container">
                <table className="ddp-search-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Search Query</th>
                      <th>Time</th>
                      <th>Time Spent</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredData.map((item, index) => (
                      <tr key={index}>
                        <td className="ddp-date-cell">{formatIndianDate(item.dateAndTime || item.timestamp)}</td>
                        <td className="ddp-query-cell">{item.query}</td>
                        <td className="ddp-time-cell">{formatIndianTime(item.dateAndTime || item.timestamp)}</td>
                        <td className="ddp-duration-cell">{formatTimeSpent(item.totalTimeSpent)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="ddp-no-results">
                <i className="fas fa-search"></i>
                <p>No search queries found for the selected period</p>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};

DetailedDataPage.propTypes = {
  location: PropTypes.shape({
    state: PropTypes.shape({
      searchData: PropTypes.array.isRequired,
      title: PropTypes.string,
      currentFilter: PropTypes.string,
      filterParams: PropTypes.shape({
        specificDate: PropTypes.string,
        customRange: PropTypes.shape({
          start: PropTypes.string,
          end: PropTypes.string
        })
      })
    })
  })
};

export default DetailedDataPage;