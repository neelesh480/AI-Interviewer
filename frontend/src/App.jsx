import React, { useState } from 'react';
import axios from 'axios';
import './App.css';
import CodeEditor from './CodeEditor';

function App() {
  const [activeTab, setActiveTab] = useState('interview'); // 'interview' or 'code'
  const [file, setFile] = useState(null);
  const [step, setStep] = useState(1); // 1: Upload, 2: Configure, 3: Result
  const [experienceLevel, setExperienceLevel] = useState('Fresher');
  const [experienceRange, setExperienceRange] = useState('2-5');
  const [questionType, setQuestionType] = useState('Mixed');
  const [detectedSkills, setDetectedSkills] = useState([]);
  const [selectedSkills, setSelectedSkills] = useState([]);
  const [jobDescription, setJobDescription] = useState('');
  const [questions, setQuestions] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleFileChange = (e) => {
    setFile(e.target.files[0]);
    setError('');
    setStep(1); // Reset to step 1 if file changes
    setDetectedSkills([]);
    setSelectedSkills([]);
  };

  const handleExperienceChange = (e) => {
    setExperienceLevel(e.target.value);
  };

  const handleRangeChange = (e) => {
    setExperienceRange(e.target.value);
  };

  const handleQuestionTypeChange = (e) => {
    setQuestionType(e.target.value);
  };

  const handleSkillToggle = (skill) => {
    if (selectedSkills.includes(skill)) {
      setSelectedSkills(selectedSkills.filter(s => s !== skill));
    } else {
      setSelectedSkills([...selectedSkills, skill]);
    }
  };

  const handleAnalyze = async () => {
    if (!file) {
      setError('Please select a CV file.');
      return;
    }

    setLoading(true);
    setError('');

    const formData = new FormData();
    formData.append('file', file);

    try {
      const response = await axios.post('/analyze', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setDetectedSkills(response.data);
      setSelectedSkills(response.data); // Select all by default
      setStep(2);
    } catch (err) {
      console.error(err);
      setError('Failed to analyze CV. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async () => {
    setLoading(true);
    setQuestions('');
    setError('');

    const formData = new FormData();
    formData.append('file', file);

    let finalExperienceLevel = experienceLevel;
    if (experienceLevel === 'Experienced') {
      finalExperienceLevel = `${experienceLevel} (Range: ${experienceRange} years)`;
    }

    formData.append('experienceLevel', finalExperienceLevel);
    formData.append('questionType', questionType);

    if (jobDescription) {
        formData.append('jobDescription', jobDescription);
    }

    selectedSkills.forEach(skill => {
        formData.append('selectedSkills', skill);
    });

    try {
      const response = await axios.post('/generate', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setQuestions(response.data);
      setStep(3);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.status === 429) {
        setError('Server is busy. Please try again in a minute.');
      } else {
        setError('Failed to generate questions. Please try again.');
      }
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

      <div className="tabs">
        <button
          className={`tab-btn ${activeTab === 'interview' ? 'active' : ''}`}
          onClick={() => setActiveTab('interview')}
        >
          Interview Questions
        </button>
        <button
          className={`tab-btn ${activeTab === 'code' ? 'active' : ''}`}
          onClick={() => setActiveTab('code')}
        >
          Code Analysis
        </button>
      </div>

      <main>
        {activeTab === 'interview' ? (
          <div className="upload-container">

            {/* Step 1: Upload & Analyze */}
            <div className="form-group">
              <label htmlFor="cv-upload">Upload CV (PDF):</label>
              <input
                type="file"
                id="cv-upload"
                accept=".pdf"
                onChange={handleFileChange}
              />
            </div>

            {step === 1 && (
              <button
                onClick={handleAnalyze}
                disabled={loading || !file}
                className="generate-btn"
              >
                {loading ? 'Analyzing...' : 'Analyze CV'}
              </button>
            )}

            {/* Step 2: Configure Options */}
            {step >= 2 && (
              <div className="configuration-section">
                <h3>Configuration</h3>

                <div className="form-group">
                  <label>Experience Level:</label>
                  <select value={experienceLevel} onChange={handleExperienceChange}>
                    <option value="Fresher">Fresher</option>
                    <option value="Experienced">Experienced</option>
                  </select>
                </div>

                {experienceLevel === 'Experienced' && (
                  <div className="form-group">
                    <label>Years of Experience:</label>
                    <select value={experienceRange} onChange={handleRangeChange}>
                      <option value="2-5">2-5 years</option>
                      <option value="5-8">5-8 years</option>
                      <option value="8-10">8-10 years</option>
                      <option value="10-15">10-15 years</option>
                      <option value=">15">More than 15 years</option>
                    </select>
                  </div>
                )}

                <div className="form-group">
                  <label>Question Type:</label>
                  <div className="radio-group">
                    <label>
                      <input
                        type="radio"
                        value="Mixed"
                        checked={questionType === 'Mixed'}
                        onChange={handleQuestionTypeChange}
                      /> Mixed
                    </label>
                    <label>
                      <input
                        type="radio"
                        value="Programming"
                        checked={questionType === 'Programming'}
                        onChange={handleQuestionTypeChange}
                      /> Programming Only
                    </label>
                    <label>
                      <input
                        type="radio"
                        value="Theoretical"
                        checked={questionType === 'Theoretical'}
                        onChange={handleQuestionTypeChange}
                      /> Theoretical Only
                    </label>
                  </div>
                </div>

                <div className="form-group">
                  <label>Job Description (Optional):</label>
                  <textarea
                    className="jd-textarea"
                    placeholder="Paste the Job Description here to tailor questions specifically to the role..."
                    value={jobDescription}
                    onChange={(e) => setJobDescription(e.target.value)}
                    rows={5}
                  />
                </div>

                {detectedSkills.length > 0 && (
                  <div className="form-group">
                    <label>Select Technical Stacks:</label>
                    <div className="skills-grid">
                      {detectedSkills.map(skill => (
                        <label key={skill} className="skill-checkbox">
                          <input
                            type="checkbox"
                            checked={selectedSkills.includes(skill)}
                            onChange={() => handleSkillToggle(skill)}
                          />
                          {skill}
                        </label>
                      ))}
                    </div>
                  </div>
                )}

                <button
                  onClick={handleGenerate}
                  disabled={loading}
                  className="generate-btn"
                >
                  {loading ? 'Generating Questions...' : 'Generate Questions'}
                </button>
              </div>
            )}

            {error && <p className="error-message">{error}</p>}

            {/* Step 3: Results */}
            {questions && (
              <div className="questions-container">
                <h2>Generated Interview Questions</h2>
                <div className="questions-content">
                  <pre>{questions}</pre>
                </div>
              </div>
            )}
          </div>
        ) : (
          <CodeEditor />
        )}
      </main>
    </div>
  );
}

export default App;