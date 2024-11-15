import torch
import utils as utils
from sklearn.model_selection import KFold
import random
import math
import evaluator as evaluator


def train_one_epoch_on(inputs: [torch.Tensor], targets: [torch.Tensor], model: torch.nn.Module, loss_function,
                       optimiser, device):
    acc_loss = 0
    n = len(inputs)
    for i, (x, y) in enumerate(zip(inputs, targets)):
        x = x.detach().to(device)
        y = y.detach().to(device)

        model.zero_grad()
        optimiser.zero_grad()
        t = model(x)
        loss = loss_function(t, y)
        loss.backward()
        optimiser.step()
        acc_loss += loss.item()

        del x
        del y

        #
        if i % 1000 == 0:
            print('\rTraining step:', ('%.2f' % ((i + 1) * 100 / n)) + '%', 'completed. | Accumulated loss:',
                  '%.2f' % acc_loss, end='\033[K')
    print()
    return acc_loss


def test_on(inputs: [torch.Tensor], targets: [torch.Tensor], model: torch.nn.Module, loss_function, is_validation=False,
            is_snip=False, device="cpu"):
    inputs_dev = list(map(lambda inp: inp.detach().to(device), inputs))
    targets_dev = list(map(lambda trg: trg.detach().to(device), targets))

    msg = 'Validation' if is_validation else 'Testing'
    if is_snip:
        msg = msg + '- Snippets'
    model.eval()
    with torch.no_grad():
        avg_acc, loss_sum, errs_map, errs_obs, errs_hist, seqs_p = \
            evaluator.acc_of_all(model, inputs_dev, targets_dev, loss_function, msg=msg)

    del inputs_dev
    del targets_dev

    model.train()
    return {
        'avg_acc': avg_acc,
        'loss_sum': loss_sum,
        'errs_map': errs_map,
        # 'errs_obs': errs_obs,
        # 'errs_hist': errs_hist,
        # 'seqs_p': seqs_p
    }


def debug_training(config: utils.Config):
    config.seed_pytorch(torch)
    if config.is_seeded:
        random.seed(config.seed_code)

    logs = {}
    for fold_num in range(3):
        train_inputs, train_targets = config.get_cache_training_of_fold(fold_num)
        val_inputs, val_targets = config.get_cache_validation_of_fold(fold_num)
        test_inputs, test_targets = config.get_cache_testing_of_fold(fold_num)
        snip_test_inputs, snip_test_targets = config.get_cache_snippets_of_fold(fold_num)

        model, optimiser, scheduler, loss_function = config.new_model_training_session()

        print('Training size:', len(train_inputs), 'Validation size:', len(val_inputs), 'Test size:', len(test_inputs))

        train_losses = []
        val_losses = []
        test_losses = []
        snippets_losses = []
        #
        train_losses.append(test_on(train_inputs, train_targets, model, loss_function, is_validation=True, device=config.device))
        val_losses.append(test_on(val_inputs, val_targets, model, loss_function, is_validation=True, device=config.device))
        test_losses.append(test_on(test_inputs, test_targets, model, loss_function, device=config.device))
        snippets_losses.append(test_on(snip_test_inputs, snip_test_targets, model, loss_function, device=config.device))
        for e in range(config.max_epochs):
            train_losses.append(
                train_one_epoch_on(train_inputs, train_targets, model, loss_function, optimiser, config.device)
            )

            val_losses.append(
                test_on(val_inputs, val_targets, model, loss_function, is_validation=True, device=config.device)
            )
            if e < config.max_epochs - 1:
                scheduler.step()

        train_losses.append(test_on(train_inputs, train_targets, model, loss_function, is_validation=True, device=config.device))
        test_losses.append(test_on(test_inputs, test_targets, model, loss_function, device=config.device))
        snippets_losses.append(test_on(snip_test_inputs, snip_test_targets, model, loss_function, is_snip=True, device=config.device))

        logs[fold_num] = {
            'train_logs': train_losses,
            'val_logs': val_losses,
            'test_logs': test_losses,
            'snippets_losses': snippets_losses}
        utils.dump_json(config.session_loss_evo_path, {'config': config.json_encode_config(), 'logs': logs})
        config.save_model_iter(model, fold_num)

    return logs
