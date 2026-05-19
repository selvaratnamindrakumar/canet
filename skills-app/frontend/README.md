# Frontend — Employee Skills Tracker

Static HTML/CSS/JavaScript frontend. No build step required.

## Pages

| File                    | Description                                   |
|-------------------------|-----------------------------------------------|
| `index.html`            | Skills dashboard — full colour-coded matrix   |
| `employees.html`        | Searchable employee card list                 |
| `employee_detail.html`  | Individual employee profile and skill editor  |

## Running Locally

The frontend communicates with the backend API at `http://localhost:8000`.  
Make sure the backend is running first (see `../backend/README.md`).

Serve the files with any static file server, for example:

```bash
# Python 3 built-in server (from the frontend/ directory)
python -m http.server 3000
```

Then open **http://localhost:3000** in your browser.

> Note: The JavaScript files use ES modules (`type="module"`), so you **must** serve them
> via a web server — opening `index.html` directly as a `file://` URL will not work due to
> browser CORS restrictions on modules.

## Configuration

The API base URL is set in `js/api.js`:

```js
export const API_BASE = 'http://localhost:8000';
```

Change this if the backend is running on a different host or port.
