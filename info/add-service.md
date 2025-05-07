# How to Add a New Service  

Follow these steps to properly add a new service to the system.  

## 1. Create a Template  
- Navigate to `base/apps/templates/`.  
- Read the `README` file in this directory for guidance.  
- Reuse an existing template to maintain consistency.  

## 2. Add Deployment Configurations  
- Based on the chosen template, create the necessary deployment configurations.  
- Add them to `environments/{env_name}/apps/` for **all required environments** (QA, staging, production, etc.).  

## 3. Update Environment Variables  
- If the service requires environment variables, update them accordingly. environments/{env_name}/configs/vars.yaml
- **Do not store API keys or sensitive credentials in these files.**  

## 4. Define a Virtual Service  
- Add a **virtual service** configuration for the new service in:  
  ```
  environments/{env_name}/istio/virtual-services
  ```  
- Ensure it follows the existing structure—copy an existing one and modify only the necessary fields.  

- Add the required domain and ensure it **strictly follows the existing pattern** how domains are assigned.  

By following these steps, you ensure the service is added correctly and consistently across environments.