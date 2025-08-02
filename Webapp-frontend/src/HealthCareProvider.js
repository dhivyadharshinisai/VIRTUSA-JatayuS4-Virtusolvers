import React, { useState, useEffect } from 'react';
import { FaStar } from 'react-icons/fa';

const HealthCareProvider = ({ onBack }) => {
  const [place, setPlace] = useState('');
  const [loading, setLoading] = useState(false);
  const [doctors, setDoctors] = useState([]);
  const [filter, setFilter] = useState('All');
  const [ratingFilter, setRatingFilter] = useState(null);
  const [userLocation, setUserLocation] = useState(null);
  const [selectedReviews, setSelectedReviews] = useState({});
  const [showReviewsFor, setShowReviewsFor] = useState(null);
  const [starHover, setStarHover] = useState({ doctorIdx: null, starIdx: null });

  useEffect(() => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const { latitude, longitude } = position.coords;
          setUserLocation({ lat: latitude, lng: longitude });
          fetchNearbyDoctors(latitude, longitude);
        },
        (error) => {
          console.warn('Geolocation permission denied:', error.message);
        }
      );
    }
  }, []);

  const calculateDistance = (lat1, lon1, lat2, lon2) => {
    const toRad = (val) => (val * Math.PI) / 180;
    const R = 6371;
    const dLat = toRad(lat2 - lat1);
    const dLon = toRad(lon2 - lon1);
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
      Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return `${(R * c).toFixed(2)} km`;
  };

  const handleSearch = async () => {
    if (!place.trim()) {
      alert('Please enter a location');
      return;
    }
    setLoading(true);
    try {
      const geoRes = await fetch(`http://localhost:5000/api/geocode?place=${encodeURIComponent(place)}`);
      const geoData = await geoRes.json();
      const location = geoData.results?.[0]?.geometry?.location;
      if (location) {
        fetchNearbyDoctors(location.lat, location.lng);
      } else {
        alert('No location found');
        setLoading(false);
      }
    } catch (err) {
      console.error('Geocode error:', err);
      setLoading(false);
      alert('Error fetching location');
    }
  };

  const fetchNearbyDoctors = async (lat, lng) => {
    try {
      const res = await fetch(`http://localhost:5000/api/nearby?lat=${lat}&lng=${lng}`);
      let data = await res.json();
      if (userLocation) {
        data = data.map((doc) => ({
          ...doc,
          distance: calculateDistance(userLocation.lat, userLocation.lng, doc.lat, doc.lng),
        }));
      }
      setDoctors(data);
    } catch (err) {
      console.error('Fetch doctors error:', err);
      alert('Failed to fetch doctors');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (doctors.length === 0) return;

    const fetchAllReviews = async () => {
      const newReviews = {};
      await Promise.all(
        doctors.map(async (doc) => {
          try {
            const res = await fetch(`http://localhost:5000/api/reviews?placeId=${doc.place_id}`);
            const data = await res.json();
            newReviews[doc.place_id] = Array.isArray(data.reviews) ? data.reviews : [];
          } catch {
            newReviews[doc.place_id] = [];
          }
        })
      );
      setSelectedReviews(newReviews);
    };

    fetchAllReviews();
  }, [doctors]);

  const handleCall = (phone) => {
    if (phone && phone !== 'N/A') {
      window.open(`tel:${phone}`);
    }
  };

  const handleDirections = (lat, lng) => {
    const url = `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;
    window.open(url, '_blank');
  };

  const handleToggleReviews = (place_id) => {
    setShowReviewsFor(showReviewsFor === place_id ? null : place_id);
  };

  const filterDoctors = () => {
    let filtered = [...doctors];
    if (filter === 'Open') filtered = filtered.filter((doc) => doc.openNow);
    else if (filter === 'Closed') filtered = filtered.filter((doc) => !doc.openNow);
    if (ratingFilter) filtered = filtered.filter((doc) => (doc.rating || 0) >= ratingFilter);
    filtered.sort((a, b) => {
      if (a.openNow !== b.openNow) return a.openNow ? -1 : 1;
      const ratingA = a.rating || 0;
      const ratingB = b.rating || 0;
      if (ratingA !== ratingB) return ratingB - ratingA;
      const distA = parseFloat((a.distance || '0').replace(' km', ''));
      const distB = parseFloat((b.distance || '0').replace(' km', ''));
      return distA - distB;
    });
    return filtered;
  };

  const sortedDoctors = filterDoctors();

  const getDropdownStyle = (dropdownType) => {
    if (dropdownType === 'status') {
      let baseStyle = {
        padding: '12px 16px',
        borderRadius: '4px',
        minWidth: '140px',
        fontWeight: 'bold',
        border: 'none',
        fontSize: '15px',
        cursor: 'pointer',
      };
      if (filter === 'Open') {
        return { ...baseStyle, backgroundColor: '#90EE90', color: '#000' };
      }
      if (filter === 'Closed') {
        return { ...baseStyle, backgroundColor: '#FF6B6B', color: '#fff' };
      }
      return { ...baseStyle, backgroundColor: '#42A5F5', color: '#fff' };
    }
    return {};
  };

  return (
    <div style={{ height: doctors.length === 0 ? '100vh' : 'auto', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      {doctors.length > 0 && (
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginTop: '30px', marginBottom: '10px' }}>
          <input
            type="text"
            value={place}
            onChange={(e) => setPlace(e.target.value)}
            placeholder="Enter location"
            style={{
              padding: '12px',
              width: '300px',
              borderRadius: '5px',
              border: '1px solid #ccc',
              fontWeight: 'bold'
            }}
          />
          <button
            onClick={handleSearch}
            style={{
              padding: '12px 20px',
              backgroundColor: '#42A5F5',
              color: '#fff',
              border: 'none',
              borderRadius: '4px',
              fontSize: '15px',
              cursor: 'pointer'
            }}
          >
            FIND DOCTOR
          </button>
          <select
            value={filter}
            onChange={(e) => {
              setFilter(e.target.value);
              setRatingFilter(null);
            }}
            style={getDropdownStyle('status')}
          >
            <option value="All">All</option>
            <option value="Open">Open</option>
            <option value="Closed">Closed</option>
          </select>
          <select
            value={ratingFilter || ''}
            onChange={(e) => setRatingFilter(parseInt(e.target.value))}
            style={{
              padding: '12px 16px',
              borderRadius: '4px',
              minWidth: '160px',
              fontWeight: 'bold',
              border: 'none',
              fontSize: '15px',
              cursor: 'pointer',
              backgroundColor: '#42A5F5',
              color: '#fff'
            }}
          >
            <option value="">Rating</option>
            <option value="5">5 Star</option>
            <option value="4">4 and above</option>
            <option value="3">3 and above</option>
          </select>
        </div>
      )}
      {loading && <p style={{ marginTop: '30px' }}>Loading...</p>}

      <div style={{ marginTop: '20px', width: '100%', maxWidth: '950px', padding: '0 15px' }}>
        {sortedDoctors.map((doc, idx) => {
          const docReviews = selectedReviews[doc.place_id] || [];
          const commentsCount = docReviews.length;
          return (
            <div
              key={idx}
              style={{
                display: 'flex',
                backgroundColor: '#fff',
                borderRadius: '10px',
                boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
                marginBottom: '20px',
                overflow: 'hidden',
                minHeight: '260px',
                height: 'auto',
                alignItems: 'stretch'
              }}
            >
              <div
                style={{
                  width: '150px',
                  minWidth: '150px',
                  maxWidth: '150px',
                  height: '260px',
                  minHeight: '260px',
                  maxHeight: '260px',
                  display: 'flex',
                  alignItems: 'stretch',
                }}
              >
                <img
                  src={doc.photoUrl || 'https://via.placeholder.com/150x260'}
                  alt={doc.name}
                  style={{
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover',
                    borderTopLeftRadius: '10px',
                    borderBottomLeftRadius: '10px',
                    display: 'block',
                  }}
                />
              </div>
              <div style={{ flex: 1, padding: '18px 18px 18px 20px', position: 'relative', minHeight: '260px' }}>
                <div style={{ marginBottom: '8px' }}>
                  <h4 style={{ margin: '0 0 4px', fontWeight: 'bold', textAlign: 'left' }}>{doc.name}</h4>
                  <p style={{ margin: 0, fontSize: '14px', textAlign: 'left' }}>Child Psychologist</p>
                </div>
                <div style={{ fontSize: '14px', textAlign: 'left' }}>
                  <p style={{ margin: '6px 0' }}><strong>Timings:</strong> {doc.hours || 'N/A'}</p>
                  <p style={{ margin: '6px 0' }}><strong>Location:</strong> {doc.address}</p>
                  <p style={{ margin: '6px 0' }}><strong>Distance:</strong> {doc.distance || 'N/A'}</p>
                  <p style={{ margin: '6px 0' }}><strong>Phone:</strong> {doc.phone}</p>
                  <p style={{ margin: '6px 0' }}>
                    <strong>Status:</strong>{' '}
                    <span style={{ color: doc.openNow ? 'green' : 'red' }}>
                      {doc.openNow ? 'Open' : 'Closed'}
                    </span>
                  </p>

                  {doc.rating && (
                    <p style={{ margin: '6px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <strong>Rating:</strong>
                      <span style={{ display: 'flex', gap: 4 }}>
                        {[...Array(5)].map((_, starIdx) => {
                          const isHovered = starHover.doctorIdx === idx && starHover.starIdx !== null && starIdx <= starHover.starIdx;
                          const isFilled = starIdx < Math.round(doc.rating);
                          return (
                            <FaStar
                              key={starIdx}
                              size={16}
                              color={isHovered ? '#FFD700' : isFilled ? '#FFD700' : '#ddd'}
                              style={{
                                cursor: 'pointer',
                                transform: isHovered ? 'scale(1.3)' : 'scale(1)',
                                transition: 'color 0.25s ease, transform 0.15s ease',
                              }}
                              onMouseEnter={() => setStarHover({ doctorIdx: idx, starIdx })}
                              onMouseLeave={() => setStarHover({ doctorIdx: null, starIdx: null })}
                            />
                          );
                        })}
                      </span>
                      <span style={{ marginLeft: 6, fontWeight: 'bold' }}>
                        {doc.rating % 1 === 0 ? `${doc.rating}/5` : `${doc.rating.toFixed(1)}/5`}
                      </span>
                    </p>
                  )}
                </div>
                <div style={{ marginTop: '10px', textAlign: 'left', display: 'flex', alignItems: 'center', gap: '18px' }}>
                  <button
                    onClick={() => handleCall(doc.phone)}
                    style={{ color: '#e91e63', border: 'none', background: 'none', cursor: 'pointer' }}
                  >
                    📞 Call
                  </button>
                  <button
                    onClick={() => handleDirections(doc.lat, doc.lng)}
                    style={{ color: '#3f51b5', border: 'none', background: 'none', cursor: 'pointer' }}
                  >
                    📍 Directions
                  </button>
                  <button
                    onClick={() => handleToggleReviews(doc.place_id)}
                    style={{ color: '#4caf50', border: 'none', background: 'none', cursor: 'pointer' }}
                  >
                    📝 Reviews
                  </button>
                  <span style={{ fontSize: "14px", color: "#555", marginLeft: 2 }}>
                    ({commentsCount} comment{commentsCount === 1 ? '' : 's'})
                  </span>
                </div>
                {showReviewsFor === doc.place_id && (
                  <div style={{
                    marginTop: '12px',
                    background: '#f5f5f5',
                    borderRadius: '6px',
                    padding: '12px',
                    maxHeight: '185px',
                    overflowY: 'auto',
                    textAlign: 'left'
                  }}>
                    <strong style={{ display: "block", marginBottom: 10 }}>Reviews</strong>
                    {commentsCount > 0 ? (
                      <ul style={{ listStyle: "none", paddingLeft: 0, margin: 0 }}>
                        {docReviews.map((review, i) => (
                          <li key={i} style={{
                            marginBottom: i === docReviews.length - 1 ? 0 : '14px',
                            borderBottom: i < docReviews.length - 1 ? '1px solid #e0e0e0' : 'none',
                            paddingBottom: 9
                          }}>
                            <div style={{ fontWeight: 600, color: "#31426e", marginBottom: 2, display: "flex", alignItems: "center" }}>
                              {review.author_name}
                              {review.rating &&
                                <span style={{ marginLeft: 8, display: 'flex' }}>
                                  {[...Array(5)].map((_, starIdx) => (
                                    <FaStar
                                      key={starIdx}
                                      size={13}
                                      color={starIdx < review.rating ? '#FFD700' : '#e0e0e0'}
                                      style={{ marginLeft: starIdx === 0 ? 3 : 0 }}
                                    />
                                  ))}
                                </span>
                              }
                            </div>
                            <div style={{ color: "#263047", fontSize: 15, margin: "2px 0 3px 0", whiteSpace:"pre-line" }}>
                              {review.text}
                            </div>
                            <div style={{ fontSize: 12, color: '#888', marginTop: 1 }}>{review.relative_time_description}</div>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p>No reviews available.</p>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default HealthCareProvider;