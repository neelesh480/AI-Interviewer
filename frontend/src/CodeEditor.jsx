import React, { useState } from 'react';
import Editor from '@monaco-editor/react';
import axios from 'axios';

const CodeEditor = () => {
  const [code, setCode] = useState('// Write your code here...');
  const [analysis, setAnalysis] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleAnalyze = async () => {
    setLoading(true);
    setAnalysis('');
    setError('');

    try {
      const response = await axios.post('/analyze-code', code, {
        headers: { 'Content-Type': 'text/plain' },
      });
      setAnalysis(response.data);
    } catch (err) {
      console.error(err);
      setError('Failed to analyze code. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="code-editor-container">
      <h3>Code Analysis & Optimization</h3>
      <div className="editor-wrapper">
        <Editor
          height="400px"
          defaultLanguage="java"
          defaultValue="// Write your code here..."
          theme="vs-dark"
          onChange={(value) => setCode(value)}
        />
      </div>
      <button
        onClick={handleAnalyze}
        disabled={loading}
        className="generate-btn"
        style={{ marginTop: '10px' }}
      >
        {loading ? 'Analyzing...' : 'Analyze Code'}
      </button>

      {error && <p className="error-message">{error}</p>}

      {analysis && (
        <div className="analysis-result">
          <h4>Analysis Result:</h4>
          <pre>{analysis}</pre>
        </div>
      )}
    </div>
  );
};

export default CodeEditor;