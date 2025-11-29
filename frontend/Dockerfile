# ----------------------------------------------------------------------------------------------------------------------
# Stage 1: Build the React application
# ----------------------------------------------------------------------------------------------------------------------
FROM node:20-alpine as builder

# Set working directory inside the container
WORKDIR /app

# Copy package.json and package-lock.json to leverage Docker layer caching
# This step only runs if package*.json changes, speeding up subsequent builds
COPY package.json ./
COPY package-lock.json ./

# Install dependencies
RUN npm ci

# Copy the rest of the source code
COPY . .

# Build the application for production
# Vite builds to the 'dist' directory by default
RUN npm run build

# ----------------------------------------------------------------------------------------------------------------------
# Stage 2: Serve the application with Nginx
# ----------------------------------------------------------------------------------------------------------------------
FROM nginx:alpine

# Copy the built assets from the builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy a custom Nginx configuration if needed (e.g., for routing or proxying API calls)
# If you need specific Nginx configurations (e.g. for React Router or API proxy),
# you can create a 'nginx.conf' file in your frontend directory and uncomment the line below.
# COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80 for web traffic
EXPOSE 80

# Command to start Nginx
CMD ["nginx", "-g", "daemon off;"]
