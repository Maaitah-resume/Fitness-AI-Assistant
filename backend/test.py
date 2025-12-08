import google.generativeai as genai
genai.configure(api_key="AIzaSyDcQnqXTYswX0xLoKU--EzsA6xnoZoebWI")

models = genai.list_models()

for m in models:
    print(m.name, " | ", m.supported_generation_methods)
