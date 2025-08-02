import React from 'react';
import './Styles/TermsPrivacy.css';
import logo from './assets/logoimg.jpeg';

const PrivacyPolicy = () => {
  return (
    <div className="legal-container">
      <header className="legal-header">
        <div className="logo-container">
          <img src={logo} alt="Safe Mind Watch Logo" className="legal-logo" />
          <h1>Safe Mind Watch</h1>
        </div>
        <h2>Privacy Policy</h2>
        <p className="effective-date">Effective: June 1, 2023</p>
      </header>

      <div className="legal-content">
        <section className="intro">
          <p>At Safe Mind Watch, we prioritize the privacy and security of your family's data. This policy explains how we collect, use, and protect information in our parental monitoring application.</p>
        </section>

        <section>
          <h3>1. Information We Collect</h3>
          <p>We collect the following data to provide our services:</p>
          <div className="data-types">
            <div className="data-category">
              <h4>Child Data</h4>
              <ul>
                <li>Online search queries and browsing history</li>
                <li>App usage patterns</li>
                <li>Device information (OS, model)</li>
                <li>Location data (when enabled)</li>
              </ul>
            </div>
            <div className="data-category">
              <h4>Parent Data</h4>
              <ul>
                <li>Account registration details</li>
                <li>Contact information</li>
                <li>Alert preferences</li>
                <li>Response to notifications</li>
              </ul>
            </div>
          </div>
        </section>

        <section>
          <h3>2. How We Use Information</h3>
          <p>We process data to:</p>
          <ul>
            <li>Identify potential mental health risks</li>
            <li>Generate activity reports for parents</li>
            <li>Improve our detection algorithms</li>
            <li>Provide emergency alerts when needed</li>
            <li>Maintain service security and integrity</li>
          </ul>
        </section>

        <section>
          <h3>3. Data Sharing & Disclosure</h3>
          <p>3.1 We <strong>do not</strong> sell or rent personal data to third parties.</p>
          <p>3.2 Limited sharing occurs with:</p>
          <ul>
            <li>Emergency services (in crisis situations)</li>
            <li>Legal authorities (when required by law)</li>
            <li>Trusted service providers (under strict contracts)</li>
          </ul>
        </section>

        <section>
          <h3>4. Data Security Measures</h3>
          <p>We implement:</p>
          <ul>
            <li>AES-256 encryption for all stored data</li>
            <li>Regular penetration testing</li>
            <li>Role-based access controls</li>
            <li>Annual third-party security audits</li>
          </ul>
        </section>

        <section>
          <h3>5. Data Retention</h3>
          <p>5.1 Routine monitoring data is retained for 30 days</p>
          <p>5.2 Alert-triggered data is kept for 1 year</p>
          <p>5.3 Parents may request earlier deletion</p>
        </section>

        <section>
          <h3>6. Parental Rights</h3>
          <p>You can:</p>
          <ul>
            <li>Access all collected child data</li>
            <li>Request correction of inaccuracies</li>
            <li>Export data in readable formats</li>
            <li>Disable specific monitoring features</li>
          </ul>
        </section>

        <section>
          <h3>7. Children's Privacy</h3>
          <p>7.1 We comply with COPPA and GDPR-K requirements</p>
          <p>7.2 Children may request:</p>
          <ul>
            <li>Explanation of monitoring</li>
            <li>Temporary monitoring pauses</li>
            <li>Age-appropriate privacy controls</li>
          </ul>
        </section>

        <section>
          <h3>8. Policy Updates</h3>
          <p>We will notify users 30 days before material changes to this policy.</p>
        </section>

        <section className="contact">
          <h3>Contact Our Privacy Team</h3>
          <address>
            Data Protection Officer<br />
            Safe Mind Watch<br />
            safemindwatch@gmail.com
            <br />
            For urgent requests, please mark emails as "PRIVACY URGENT"
          </address>
        </section>
      </div>
    </div>
  );
};

export default PrivacyPolicy;