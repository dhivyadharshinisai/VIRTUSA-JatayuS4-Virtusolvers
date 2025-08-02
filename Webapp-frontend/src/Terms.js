import React from 'react';
import './Styles/TermsPrivacy.css';
import logo from './assets/logoimg.jpeg';

const Terms = () => {
  return (
    <div className="legal-container">
      <header className="legal-header">
        <div className="logo-container">
          <img src={logo} alt="Safe Mind Watch Logo" className="legal-logo" />
          <h1>Safe Mind Watch</h1>
        </div>
        <h2>Terms of Service</h2>
      </header>

      <div className="legal-content">
        <section className="intro">
          <p>Welcome to Safe Mind Watch, a parental monitoring application designed to help protect children's mental health and online safety. By using our services, you agree to these legally binding terms.</p>
        </section>

        <section>
          <h3>1. Service Description</h3>
          <p>Safe Mind Watch provides:</p>
          <ul>
            <li>Real-time monitoring of child's online activities</li>
            <li>AI-powered mental health risk detection</li>
            <li>Emergency alert system for parents</li>
            <li>Activity reports and analytics</li>
          </ul>
        </section>

        <section>
          <h3>2. Eligibility Requirements</h3>
          <p>2.1 You must be:</p>
          <ul>
            <li>A parent or legal guardian of the monitored child</li>
            <li>At least 18 years old</li>
            <li>Legally authorized to monitor the child</li>
          </ul>
          <p>2.2 You must obtain proper consent from the child where required by law.</p>
        </section>

        <section>
          <h3>3. Prohibited Conduct</h3>
          <p>You agree not to:</p>
          <ul>
            <li>Monitor individuals without legal authority</li>
            <li>Use the service for commercial surveillance</li>
            <li>Reverse engineer or modify our software</li>
            <li>Share login credentials with unauthorized parties</li>
          </ul>
        </section>

        <section>
          <h3>4. Data Protection</h3>
          <p>4.1 We implement:</p>
          <ul>
            <li>End-to-end encryption for all data</li>
            <li>Regular security audits</li>
            <li>Strict access controls</li>
          </ul>
          <p>4.2 Data retention is limited to 30 days unless an alert is triggered.</p>
        </section>

        <section>
          <h3>5. Parental Responsibilities</h3>
          <p>You agree to:</p>
          <ul>
            <li>Use alerts responsibly and verify concerns</li>
            <li>Seek professional help when needed</li>
            <li>Explain monitoring to your child appropriately</li>
          </ul>
        </section>

        <section>
          <h3>6. Limitation of Liability</h3>
          <p>Safe Mind Watch is not liable for:</p>
          <ul>
            <li>Missed alerts or false negatives</li>
            <li>Actions taken based on our alerts</li>
            <li>Technical issues beyond our control</li>
          </ul>
        </section>

        <section>
          <h3>7. Governing Law</h3>
          <p>These Terms are governed by Indian law, with jurisdiction in Chennai courts.</p>
        </section>

        <section className="contact">
          <h3>Contact Us</h3>
          <p>For questions about these Terms:</p>
          <address>
            Safe Mind Watch Legal Team<br />
            safemindwatch@gmail.com
          </address>
        </section>
      </div>
    </div>
  );
};

export default Terms;