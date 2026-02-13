# AI Interviewer

AI Interviewer is a full-stack application that generates tailored technical interview questions based on a candidate's CV. It uses Google's Gemini AI to analyze the CV and create relevant questions for different experience levels and technical stacks.

## Features

*   **CV Analysis**: Automatically extracts technical skills from uploaded PDF CVs.
*   **Tailored Questions**: Generates questions based on experience level (Fresher/Experienced) and specific tech stacks.
*   **Question Types**: Choose between Programming, Theoretical, or Mixed questions.
*   **Rate Limiting**: Intelligent queue system to handle API rate limits gracefully.
*   **Modern UI**: React-based frontend for a smooth user experience.

## Prerequisites

Before you begin, ensure you have the following installed:
*   **Java 11** or higher
*   **Maven** (for building the backend)
*   **Node.js** and **npm** (for the frontend)
*   A **Google Gemini API Key** (Get one [here](https://aistudio.google.com/app/apikey))

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/neelesh480/AI-Interviewer.git
cd AI-Interviewer
```

### 2. Backend Setup (Spring Boot)

1.  Navigate to the project root directory.
2.  Open `src/main/resources/application.properties`.
3.  Replace `YOUR_GEMINI_API_KEY` with your actual Google Gemini API key:
    ```properties
    google.gemini.key=YOUR_ACTUAL_API_KEY_HERE
    google.gemini.model=gemini-flash-latest
    ```
4.  Build and run the application:
    ```bash
    mvn spring-boot:run
    ```
    The backend server will start on `http://localhost:8080`.

### 3. Frontend Setup (React + Vite)

1.  Open a new terminal window.
2.  Navigate to the `frontend` directory:
    ```bash
    cd frontend
    ```
3.  Install dependencies:
    ```bash
    npm install
    ```
4.  Start the development server:
    ```bash
    npm run dev
    ```
    The frontend will be available at `http://localhost:3000`.

## Usage

1.  Open your browser and go to `http://localhost:3000`.
2.  **Upload CV**: Select a PDF file of the candidate's CV.
3.  **Analyze**: Click "Analyze CV" to extract technical skills.
4.  **Configure**:
    *   Select **Experience Level** (Fresher/Experienced).
    *   If Experienced, select the **Years of Experience**.
    *   Choose **Question Type** (Mixed, Programming, Theoretical).
    *   Select specific **Technical Skills** to focus on.
5.  **Generate**: Click "Generate Questions" and wait for the AI to create the interview questions.

## Troubleshooting

*   **429 Too Many Requests**: The application has a built-in rate limiter and retry mechanism. If you see this error, wait a few moments and try again. The server limits concurrent requests to 10 to prevent overloading.
*   **PDF Parsing Errors**: Ensure the uploaded file is a valid, non-encrypted PDF.

## License

This project is licensed under the MIT License.
