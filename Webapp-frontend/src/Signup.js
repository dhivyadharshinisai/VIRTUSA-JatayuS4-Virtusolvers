import React, { useState, useEffect } from 'react';
import { AiFillEye, AiFillEyeInvisible } from 'react-icons/ai';
import { GoogleLogin } from '@react-oauth/google';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { toast } from 'react-toastify';
import { jwtDecode } from 'jwt-decode';
import './Styles/Signup.css';

const Signup = ({ onSwitchToLogin }) => {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    name: '',
    email: '',
    phone: '',
    emailOtp: '',
    phoneOtp: '',
    password: '',
    confirmPassword: '',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [showCPassword, setShowCPassword] = useState(false);
  const [emailOtpSent, setEmailOtpSent] = useState(false);
  const [emailOtpVerified, setEmailOtpVerified] = useState(false);
  const [phoneOtpSent, setPhoneOtpSent] = useState(false);
  const [phoneOtpVerified, setPhoneOtpVerified] = useState(false);
  const [vonageRequestId, setVonageRequestId] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPhoneSection, setShowPhoneSection] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);

  useEffect(() => {
    if (phoneOtpVerified) {
      toast.success('✅ User created successfully!');
      const timer = setTimeout(() => navigate('/'), 3000);
      return () => clearTimeout(timer);
    }
  }, [phoneOtpVerified, navigate]);

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
    setError('');
  };

  const handleSendEmailOtp = async () => {
    if (!acceptedTerms) {
      setError('You must accept the terms and conditions');
      return;
    }
    if (!form.email.match(/^[^\s@]+@[^\s@]+\.[^\s@]+$/)) {
      setError('Enter a valid email address');
      return;
    }
    
    try {
      setLoading(true);
      const response = await axios.post('http://localhost:5000/signup/request-email-otp', {
        email: form.email,
        name: form.name,
        password: form.password
      });
      
      if (response.data.success) {
        setEmailOtpSent(true);
        toast.success('Email OTP sent');
      } else {
        setError(response.data.message || 'Failed to send email OTP');
      }
    } catch (err) {
      if (err.response) {
        setError(err.response.data.message || `Server error: ${err.response.status}`);
      } else if (err.request) {
        setError('Network error - is the backend server running?');
      } else {
        setError('Request setup error: ' + err.message);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyEmailOtp = async () => {
    if (!form.emailOtp || form.emailOtp.length !== 6) {
      setError('Enter a valid 6-digit OTP');
      return;
    }
    try {
      const response = await axios.post('http://localhost:5000/signup/verify-email-otp', {
        email: form.email,
        otp: form.emailOtp,
      });
      if (response.data.success) {
        setEmailOtpVerified(true);
        toast.success('Email verified');
      } else {
        setError(response.data.message || 'Invalid email OTP');
      }
    } catch (err) {
      setError(err.response?.data?.message || 'OTP verification failed');
    }
  };

  const handleProceedToPhoneSection = () => {
    if (!form.password || form.password !== form.confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    setShowPhoneSection(true);
  };

  const handleSendPhoneOtp = async () => {
    if (!form.phone.match(/^\d{10}$/)) {
      setError('Enter a valid 10-digit phone number');
      return;
    }
    try {
      const response = await axios.post('http://localhost:5000/api/otp/send-otp', {
        phoneNumber: `+91${form.phone}`,
      });
      if (response.data.success) {
        setPhoneOtpSent(true);
        setVonageRequestId(response.data.requestId);
        toast.success('OTP sent to your phone');
      } else {
        setError(response.data.message || 'Failed to send phone OTP');
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Phone OTP sending failed');
    }
  };

  const handleVerifyPhoneOtp = async () => {
    if (!form.phoneOtp || form.phoneOtp.length !== 4) {
      setError('Enter a valid 4-digit OTP');
      return;
    }
    try {
      setLoading(true);
      const response = await axios.post('http://localhost:5000/api/otp/verify-otp', {
        requestId: vonageRequestId,
        code: form.phoneOtp,
        email: form.email,
        phone: form.phone,
        name: form.name,
        password: form.password,
      });
      if (response.data.success) {
        setPhoneOtpVerified(true);
      } else {
        setError(response.data.message || 'Invalid OTP');
      }
    } catch (err) {
      setError(err.response?.data?.message || 'OTP verification failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="signup-page">
      <div className="signup-container">
        <div className="signup-card">
          <h1 className="signup-title">Sign Up</h1>
          
          {!showPhoneSection ? (
            <form onSubmit={(e) => e.preventDefault()} className="signup-form">
              <input 
                type="text" 
                name="name" 
                value={form.name} 
                onChange={handleChange} 
                placeholder="Enter your Name" 
                className="signup-input"
                required 
              />
              
              <div className="otp-field">
                <input
                  type="email"
                  name="email"
                  value={form.email}
                  onChange={handleChange}
                  placeholder="Enter your Email"
                  className="signup-input"
                  required
                  disabled={emailOtpVerified}
                />
                <button
                  type="button"
                  onClick={handleSendEmailOtp}
                  disabled={emailOtpSent || emailOtpVerified || !form.name || !form.password}
                  className="otp-button"
                >
                  {emailOtpVerified ? 'Verified' : emailOtpSent ? 'OTP Sent' : 'Send OTP'}
                </button>
              </div>
              
              {emailOtpSent && !emailOtpVerified && (
                <div className="otp-field">
                  <input 
                    type="text" 
                    name="emailOtp" 
                    value={form.emailOtp} 
                    onChange={handleChange} 
                    placeholder="Enter Email OTP" 
                    maxLength={6} 
                    className="signup-input"
                  />
                  <button type="button" onClick={handleVerifyEmailOtp} className="otp-button">Verify</button>
                </div>
              )}
              
              <div className="password-field">
                <input 
                  type={showPassword ? 'text' : 'password'} 
                  name="password" 
                  value={form.password} 
                  onChange={handleChange} 
                  placeholder="Password" 
                  className="signup-input"
                  required 
                />
                <span 
                  onClick={() => setShowPassword(!showPassword)} 
                  className="password-toggle"
                >
                  {showPassword ? <AiFillEyeInvisible /> : <AiFillEye />}
                </span>
              </div>
              
              <div className="password-field">
                <input 
                  type={showCPassword ? 'text' : 'password'} 
                  name="confirmPassword" 
                  value={form.confirmPassword} 
                  onChange={handleChange} 
                  placeholder="Confirm Password" 
                  className="signup-input"
                  required 
                />
                <span 
                  onClick={() => setShowCPassword(!showCPassword)} 
                  className="password-toggle"
                >
                  {showCPassword ? <AiFillEyeInvisible /> : <AiFillEye />}
                </span>
              </div>
              
              <div className="terms-checkbox">
                <input 
                  type="checkbox" 
                  id="terms" 
                  checked={acceptedTerms} 
                  onChange={(e) => setAcceptedTerms(e.target.checked)}
                />
                <label htmlFor="terms">
                  I agree to the{' '}
                  <a 
                    href="/terms" 
                    className="terms-link"
                    target="_blank" 
                    rel="noopener noreferrer"
                  >
                    Terms
                  </a>{' '}
                  and{' '}
                  <a href="/privacy" className="terms-link" target="_blank" rel="noopener noreferrer">
                    Privacy Policy
                  </a>
                </label>
              </div>
              
              <button 
                type="button" 
                className="signup-button" 
                onClick={handleProceedToPhoneSection} 
                disabled={!emailOtpVerified || !acceptedTerms}
              >
                Proceed
              </button>
            </form>
          ) : (
            <>
              <div className="otp-field">
                <input 
                  type="tel" 
                  name="phone" 
                  value={form.phone} 
                  onChange={handleChange} 
                  placeholder="Phone Number" 
                  maxLength={10} 
                  className="signup-input"
                  required 
                  disabled={phoneOtpVerified} 
                />
                <button 
                  type="button" 
                  onClick={handleSendPhoneOtp} 
                  disabled={phoneOtpSent || phoneOtpVerified} 
                  className="otp-button"
                > 
                  {phoneOtpVerified ? 'Verified' : phoneOtpSent ? 'OTP Sent' : 'Send OTP'} 
                </button>
              </div>
              
              {phoneOtpSent && !phoneOtpVerified && (
                <div className="otp-field">
                  <input 
                    type="text" 
                    name="phoneOtp" 
                    value={form.phoneOtp} 
                    onChange={handleChange} 
                    placeholder="Enter Phone OTP" 
                    maxLength={4} 
                    className="signup-input"
                  />
                  <button type="button" onClick={handleVerifyPhoneOtp} className="otp-button">Verify</button>
                </div>
              )}
              
              <button 
                type="button" 
                className="signup-button" 
                onClick={handleVerifyPhoneOtp} 
                disabled={loading || phoneOtpVerified}
              >
                {loading ? 'Processing...' : 'Verify & Signup'}
              </button>
            </>
          )}
          
          <p className="divider">OR</p>
          
          <div className="google-auth">
            <GoogleLogin
              onSuccess={async (credentialResponse) => {
                if (!acceptedTerms) {
                  setError('You must accept the terms and conditions');
                  return;
                }
                
                const decoded = jwtDecode(credentialResponse.credential);
                const { name, email } = decoded;
                const generatedPassword = Math.random().toString(36).slice(-8);

                try {
                  const res = await axios.post('http://localhost:5000/api/signup/google-init', {
                    name,
                    email,
                    password: generatedPassword,
                  });

                  if (res.data.success) {
                    setForm({
                      name,
                      email,
                      password: generatedPassword,
                      confirmPassword: generatedPassword,
                      phone: '',
                      phoneOtp: '',
                      emailOtp: '',
                    });
                    setEmailOtpVerified(true);
                    setShowPhoneSection(true);
                    toast.success('Google login successful. Please verify your phone number');
                  } else {
                    toast.error(res.data.message);
                  }
                } catch (err) {
                  toast.error(err.response?.data?.message || 'Google signup failed');
                }
              }}
            />
          </div>
          
          <p className="login-redirect">
            Already have an account?{' '}
            <span onClick={() => navigate('/')}>Login</span>
          </p>
          
          {error && <p className="error-message">{error}</p>}
        </div>
      </div>
    </div>
  );
};

export default Signup;