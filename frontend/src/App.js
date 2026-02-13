import React, { useState } from 'react';
import axios from 'axios';
import './App.css';

function App() {
  const [file, setFile] = useState(null);
  const [experienceLevel, setExperienceLevel] = useState('Fresher');
  const [experienceRange, setExperienceRange] = useState('2-5');
  const [questions, setQuestions] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleFileChange = (e) => {
    setFile(e.target.files[0]);
    setError('');
  };

  const handleExperienceChange = (e) => {
    setExperienceLevel(e.target.value);
  };

  const handleRangeChange = (e) => {
    setExperienceRange(e.target.value);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!file) {
      setError('Please select a CV file.');
      return;
    }

    setLoading(true);
    setQuestions('');
    setError('');

    const formData = new FormData();
    formData.append('file', file);

    // If experienced, send "Experienced (Range: X-Y years)"
    // If fresher, just send "Fresher"
    let finalExperienceLevel = experienceLevel;
    if (experienceLevel === 'Experienced') {
      finalExperienceLevel = `${experienceLevel} (Range: ${experienceRange} years)`;
    }

    formData.append('experienceLevel', finalExperienceLevel);

    try {
      // Use relative path so Vite proxy handles it
      const response = await axios.post('/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      setQuestions(response.data);
    } catch (err) {
      console.error(err);
      setError('Failed to generate questions. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>AI Interviewer</h1>
        <p>Upload your CV to generate tailored interview questions.</p>
      </header>

      <main>
        <div className="upload-container">
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="cv-upload">Upload CV (PDF):</label>
              <input
                type="file"
                id="cv-upload"
                accept=".pdf"
                onChange={handleFileChange}
              />
            </div>

            <div className="form-group">
              <label htmlFor="experience-level">Experience Level:</label>
              <select
                id="experience-level"
                value={experienceLevel}
                onChange={handleExperienceChange}
              >
                <option value="Fresher">Fresher</option>
                <option value="Experienced">Experienced</option>
              </select>
            </div>

            {experienceLevel === 'Experienced' && (
              <div className="form-group">
                <label htmlFor="experience-range">Years of Experience:</label>
                <select
                  id="experience-range"
                  value={experienceRange}
                  onChange={handleRangeChange}
                >
                  <option value="2-5">2-5 years</option>
                  <option value="5-8">5-8 years</option>
                  <option value="8-10">8-10 years</option>
                  <option value="10-15">10-15 years</option>
                  <option value=">15">More than 15 years</option>
                </select>
              </div>
            )}

            <button type="submit" disabled={loading} className="generate-btn">
              {loading ? 'Generating...' : 'Generate Questions'}
            </button>
          </form>
          {error && <p className="error-message">{error}</p>}
        </div>

        {questions && (
          <div className="questions-container">
            <h2>Generated Interview Questions</h2>
            <div className="questions-content">
              <pre>{questions}</pre>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;