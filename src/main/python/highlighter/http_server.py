from flask import Flask, request
import torch
import time
#
from pygments import highlight
from pygments.lexers import Python3Lexer, JavaLexer, KotlinLexer, JavascriptLexer, CSharpLexer, CppLexer
#
import utils as utils
import pygments_utils as pygments_utils


app = Flask(__name__)

# Model's
device = None
model = None

# Pygments'
lang_lexer = None
drop_formatter = pygments_utils.DropFormatter()
json_formatter = None


@app.post("/load_model")
def load_model():
    global device
    global model

    model_log_name: str = request.json["model_log_name"]
    model_index: int = request.json["model_index"]

    # Load config of trained model.
    log = utils.load_json(f"../saved_model_losses/{model_log_name}.json")

    # Rebuild original config
    config = utils.Config()
    config.apply_config_of(log['config'])

    device = config.device

    # Retrieve the last trained model.
    model = config.get_model_of_iter(model_index)
    model.eval()

    # print(f"| Loaded {model_log_name} :: {model_index}")

    return "loaded"


@app.post("/eval_model")
def eval_model():
    with torch.no_grad():
        global device
        global model

        input_token_ids: list[int] = request.json["input_token_ids"]
        # print(f"> ({time.time()}) new input len: '{len(input_token_ids)}'")

        # Create non negative input.
        token_rules = list(map(lambda x: int(x) + 1, input_token_ids))
        #
        t0 = time.time_ns()
        tens_token_rules = torch.tensor(token_rules, dtype=torch.long).to(device)
        ps = torch.argmax(model(tens_token_rules), dim=1)
        t1 = time.time_ns()
        #
        model_cmp_time_ns = round(t1 - t0)

        # print(f"< completed in ns: {model_cmp_time_ns}")

        return {
            "ns": model_cmp_time_ns,
            "ps": [thc.item() for thc in ps]
        }


@app.post("/load_pygments")
def load_pygments():
    global lang_lexer
    global json_formatter

    lang = request.json["lang"]
    if lang == 'java':
        lang_lexer = JavaLexer()
        bindings = pygments_utils.JAVA_ORACLE_BINDINGS
    elif lang == 'kotlin':
        lang_lexer = KotlinLexer()
        bindings = pygments_utils.KOTLIN_ORACLE_BINDINGS
    elif lang == 'python3':
        lang_lexer = Python3Lexer()
        bindings = pygments_utils.PYTHON3_ORACLE_BINDINGS
    elif lang == 'javascript':
        lang_lexer = JavascriptLexer()
        bindings = pygments_utils.JS_ORACLE_BINDINGS
    elif lang == 'csharp':
        lang_lexer = CSharpLexer()
        bindings = pygments_utils.CS_ORACLE_BINDINGS
    elif lang == 'cpp':
        lang_lexer = CppLexer()
        bindings = pygments_utils.CPP_ORACLE_BINDINGS
    else:
        raise ValueError(lang + 'is not a valid language')

    json_formatter = pygments_utils.JSONFormatter(bindings)

    return "loaded"


@app.post("/eval_pygments")
def eval_pygments():
    global lang_lexer
    global drop_formatter
    global json_formatter

    src = request.json["src"]

    t0 = time.time_ns()
    highlight(src, lang_lexer, drop_formatter)
    t1 = time.time_ns()

    res_json = highlight(src, lang_lexer, json_formatter)

    return {
        "ns": t1 - t0,
        "res_json": res_json
    }
